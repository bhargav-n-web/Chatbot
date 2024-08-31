import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ChatbotClient extends JFrame {
    private JTextArea chatArea;
    private JTextField userInput;
    private JButton sendButton;
    private JButton fileButton;
    private SSLSocket socket;
    private PrintWriter out;
    private BufferedReader in;
    private DataInputStream dis;
    private DataOutputStream dos;

    public ChatbotClient() {
        // Setup GUI
        setTitle("Chatbot Client");
        setSize(400, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        userInput = new JTextField(30);
        sendButton = new JButton("Send");
        fileButton = new JButton("Send File");

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());
        inputPanel.add(userInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(fileButton, BorderLayout.WEST);

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        fileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendFile();
            }
        });

        connectToServer();
    }

    private void connectToServer() {
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) factory.createSocket("localhost", 12345);

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            // Start a background thread to listen for server responses
            new Thread(new IncomingMessageHandler()).start();
        } catch (IOException e) {
            chatArea.append("Error: Unable to connect to the server. Retrying...\n");
            retryConnection();
        }
    }

    private void retryConnection() {
        Timer timer = new Timer(5000, new ActionListener() { // Retry every 5 seconds
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToServer();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void sendMessage() {
        try {
            String userText = sanitizeInput(userInput.getText());
            if (!userText.trim().isEmpty()) {
                chatArea.append("You: " + userText + "\n");
                out.println(userText); // Send message to server
                userInput.setText("");
            }
        } catch (Exception e) {
            chatArea.append("Error: Unable to send message. Please try again.\n");
            e.printStackTrace();
        }
    }

    private void sendFile() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(this);

            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                dos.writeUTF("FILE_TRANSFER " + selectedFile.getName());
                dos.writeLong(selectedFile.length());

                try (FileInputStream fis = new FileInputStream(selectedFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) > 0) {
                        dos.write(buffer, 0, read);
                    }
                }
                chatArea.append("File sent: " + selectedFile.getName() + "\n");
            }
        } catch (IOException e) {
            chatArea.append("Error: Unable to send file. Please try again.\n");
            e.printStackTrace();
        }
    }

    private String sanitizeInput(String input) {
        return input.replaceAll("[^a-zA-Z0-9\\s]", "");
    }

    private class IncomingMessageHandler implements Runnable {
        @Override
        public void run() {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    if (response.startsWith("FILE_TRANSFER")) {
                        receiveFile(response);
                    } else {
                        chatArea.append("Bot: " + response + "\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void receiveFile(String fileMessage) {
        try {
            String fileName = fileMessage.split(" ")[1];
            long fileSize = dis.readLong();
            File file = new File("client_files/" + fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileSize;

                while ((read = dis.read(buffer, 0, Math.min(buffer.length, (int) remaining))) > 0) {
                    remaining -= read;
                    fos.write(buffer, 0, read);
                }
            }
            chatArea.append("File received: " + fileName + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ChatbotClient().setVisible(true);
            }
        });
    }
}
