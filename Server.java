import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class ChatbotServer {
    private static final int PORT = 12345;
    private Connection connection;
    private ExecutorService pool;

    public ChatbotServer() {
        try {
            // Establish JDBC connection using HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/chatbot_db");
            config.setUsername("root");
            config.setPassword("password"); // Update with your DB password
            config.setMaximumPoolSize(10);

            HikariDataSource dataSource = new HikariDataSource(config);
            connection = dataSource.getConnection();

            // Initialize the thread pool
            pool = Executors.newFixedThreadPool(10);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            // Use SSLServerSocket for secure communication
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket, connection));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ChatbotServer().start();
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private Connection connection;

    public ClientHandler(Socket socket, Connection connection) {
        this.socket = socket;
        this.connection = connection;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String userMessage;
            loadChatHistory(out);

            while ((userMessage = in.readLine()) != null) {
                userMessage = sanitizeInput(userMessage);

                if (userMessage.startsWith("FILE_TRANSFER")) {
                    receiveFile(dis, userMessage);
                } else if (userMessage.startsWith("SEND_FILE")) {
                    String filePath = userMessage.substring(10);
                    sendFile(dos, filePath);
                } else {
                    String botResponse = generateResponse(userMessage);

                    // Save the conversation to the database
                    saveToDatabase(userMessage, botResponse);

                    // Send the response back to the client
                    out.println(botResponse);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String sanitizeInput(String input) {
        return input.replaceAll("[^a-zA-Z0-9\\s]", "");
    }

    private String generateResponse(String userMessage) {
        // Basic bot logic (expand as needed)
        if (userMessage.matches("(?i).*\\bhello\\b.*")) {
            return "Hi there!";
        } else if (userMessage.matches("(?i).*\\bhow are you\\b.*")) {
            return "I'm a bot, but I'm functioning perfectly!";
        } else {
            return "I'm not sure how to respond to that.";
        }
    }

    private void saveToDatabase(String userMessage, String botResponse) {
        String query = "INSERT INTO messages (user_message, bot_response) VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, userMessage);
            stmt.setString(2, botResponse);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadChatHistory(PrintWriter out) {
        String query = "SELECT user_message, bot_response FROM messages ORDER BY timestamp ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                out.println("You: " + rs.getString("user_message"));
                out.println("Bot: " + rs.getString("bot_response"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void receiveFile(DataInputStream dis, String userMessage) {
        try {
            String fileName = userMessage.split(" ")[1];
            long fileSize = dis.readLong();
            File file = new File("server_files/" + fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileSize;

                while ((read = dis.read(buffer, 0, Math.min(buffer.length, (int) remaining))) > 0) {
                    remaining -= read;
                    fos.write(buffer, 0, read);
                }
            }
            System.out.println("File " + fileName + " received from client.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile(DataOutputStream dos, String filePath) {
        try {
            File file = new File(filePath);
            dos.writeUTF("FILE_TRANSFER " + file.getName() + " " + file.length());
            dos.flush();

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    dos.write(buffer, 0, read);
                    dos.flush();
                }
            }
            System.out.println("File " + file.getName() + " sent to client.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
