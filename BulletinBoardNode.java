import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Classe principal que representa um nó do sistema
public class BulletinBoardNode {
    private static final String HOST = "127.0.0.1";
    private final int port;
    private final List<Integer> replicationPorts;
    private final CopyOnWriteArrayList<Message> wall = new CopyOnWriteArrayList<>();
    private final Map<String, String> users = new HashMap<>();
    private final Map<String, String> authenticatedUsers = new ConcurrentHashMap<>();
    private boolean isRunning = true;
    private ServerSocket serverSocket;

    public BulletinBoardNode(int port, List<Integer> replicationPorts) {
        this.port = port;
        this.replicationPorts = replicationPorts;

        // Dados de autenticação simples
        users.put("user1", "pass1");
        users.put("user2", "pass2");
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Nó iniciado e ouvindo em " + HOST + ":" + port);

            while (isRunning) {
                Socket socket = serverSocket.accept();

                // Trata cada nova conexão em uma thread separada
                Thread handlerThread = new Thread(() -> handleConnection(socket));
                handlerThread.start();
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            System.out.println("Conexão aceita de " + socket.getInetAddress().getHostAddress());

            // Lógica para diferenciar clientes de outros nós
            // O primeiro objeto enviado determina o tipo de conexão.
            String connectionType = in.readObject().toString();

            if ("CLIENT".equals(connectionType)) {
                handleClient(socket, in, out);
            } else if ("REPLICATION".equals(connectionType)) {
                handleReplication(socket, in);
            } else if ("RECONCILIATION_REQUEST".equals(connectionType)) {
                handleReconciliationRequest(in, out);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro na comunicação com " + socket.getInetAddress().getHostAddress() + ": " + e.getMessage());
        }
    }

    private void handleClient(Socket socket, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        out.writeObject("Bem-vindo ao Mural Distribuído! Autentique-se para postar, ou digite 'ler' para ver o mural.");

        String clientIp = socket.getInetAddress().getHostAddress();

        while (true) {
            try {
                String commandLine = (String) in.readObject();
                String[] parts = commandLine.split(" ", 2);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "login":
                        if (parts.length > 1) {
                            authenticate(out, parts[1], clientIp);
                        } else {
                            out.writeObject("Uso: login <usuario> <senha>");
                        }
                        break;
                    case "postar":
                        if (isAuthenticated(clientIp)) {
                            if (parts.length > 1) {
                                postMessage(out, parts[1], authenticatedUsers.get(clientIp));
                            } else {
                                out.writeObject("Uso: postar <mensagem> [privado]");
                            }
                        } else {
                            out.writeObject("Erro: Você precisa estar logado para postar.");
                        }
                        break;
                    case "ler":
                        readWall(out, clientIp);
                        break;
                    case "exit":
                        authenticatedUsers.remove(clientIp);
                        return;
                    default:
                        out.writeObject("Comando desconhecido. Comandos: login <usuario> <senha>, postar <mensagem> [privado], ler, exit.");
                        break;
                }
            } catch (SocketException e) {
                // Conexão encerrada pelo cliente
                System.out.println("Conexão com cliente em " + clientIp + " encerrada.");
                return;
            }
        }
    }

    private void handleReplication(Socket socket, ObjectInputStream in) throws IOException, ClassNotFoundException {
        System.out.println("Conexão de replicação aceita de " + socket.getInetAddress().getHostAddress());

        while (true) {
            try {
                Object obj = in.readObject();
                if (obj instanceof Message) {
                    Message msg = (Message) obj;
                    // Adiciona a mensagem ao mural local, se ela não existir
                    if (!wall.contains(msg)) {
                        wall.add(msg);
                        System.out.println("Replicação: Recebida nova mensagem de " + msg.getAuthor() + ": '" + msg.getContent() + "'");
                    }
                }
            } catch (SocketException e) {
                System.out.println("Conexão de replicação com " + socket.getInetAddress().getHostAddress() + " encerrada.");
                return;
            }
        }
    }

    private void handleReconciliationRequest(ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        try {
            long lastSeenTimestamp = (Long) in.readObject();
            List<Message> missingMessages = new ArrayList<>();
            // Encontra e envia todas as mensagens que o nó solicitante perdeu
            for (Message msg : wall) {
                if (msg.getTimestamp() > lastSeenTimestamp) {
                    missingMessages.add(msg);
                }
            }
            // Envia a lista de mensagens faltantes de volta para o nó solicitante
            out.writeObject(missingMessages);
            System.out.println("Pedido de reconciliação atendido. Enviadas " + missingMessages.size() + " mensagens.");
        } catch (IOException e) {
            System.err.println("Erro ao atender pedido de reconciliação: " + e.getMessage());
        }
    }

    private void authenticate(ObjectOutputStream out, String loginData, String clientIp) throws IOException {
        String[] loginParts = loginData.split(" ");
        if (loginParts.length != 2) {
            out.writeObject("Uso: login <usuario> <senha>");
            return;
        }

        String username = loginParts[0];
        String password = loginParts[1];

        if (users.getOrDefault(username, "").equals(password)) {
            authenticatedUsers.put(clientIp, username);
            out.writeObject("Login bem-sucedido, " + username + "! Agora você pode postar mensagens.");
            System.out.println("Usuário '" + username + "' logado de " + clientIp);
        } else {
            out.writeObject("Login falhou. Verifique seu usuário e senha.");
            System.out.println("Tentativa de login falhou de " + clientIp + " com usuário '" + username + "'");
        }
    }

    private boolean isAuthenticated(String clientIp) {
        return authenticatedUsers.containsKey(clientIp);
    }

    private void postMessage(ObjectOutputStream out, String content, String author) throws IOException {
        // Verifica se a mensagem deve ser privada
        boolean isPrivate = content.toLowerCase().endsWith(" privado");
        String finalContent = content;

        if (isPrivate) {
            finalContent = content.substring(0, content.length() - 8).trim();
        }

        Message newMessage = new Message(author, finalContent, isPrivate);
        wall.add(newMessage);
        System.out.println("Mensagem postada por " + author + ": '" + finalContent + "'" + (isPrivate ? " (privada)" : ""));
        out.writeObject("Mensagem postada com sucesso.");

        // Inicia a replicação assíncrona
        Thread replicationThread = new Thread(() -> replicateMessage(newMessage));
        replicationThread.start();
    }

    private void readWall(ObjectOutputStream out, String clientIp) throws IOException {
        boolean loggedIn = isAuthenticated(clientIp);
        
        List<Message> visibleMessages = new ArrayList<>();
        for (Message msg : wall) {
            if (!msg.isPrivate() || loggedIn) {
                visibleMessages.add(msg);
            }
        }
        
        if (visibleMessages.isEmpty()) {
            out.writeObject("O mural está vazio.");
            return;
        }

        StringBuilder wallContent = new StringBuilder("--- Mural de Mensagens ---\n");
        for (Message msg : visibleMessages) {
            wallContent.append(msg.getAuthor()).append(": ").append(msg.getContent()).append("\n");
        }
        wallContent.append("--------------------------\n");
        out.writeObject(wallContent.toString());
    }

    private void replicateMessage(Message message) {
        for (int targetPort : replicationPorts) {
            try (Socket socket = new Socket(HOST, targetPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                // Sinaliza que é uma conexão de replicação
                out.writeObject("REPLICATION");
                out.writeObject(message);

                System.out.println("Mensagem replicada para o nó na porta " + targetPort);
            } catch (ConnectException e) {
                System.err.println("Aviso: Falha ao replicar para o nó na porta " + targetPort + ". Ele pode estar offline.");
            } catch (IOException e) {
                System.err.println("Erro ao replicar para " + targetPort + ": " + e.getMessage());
            }
        }
    }

    public void reconcile() {
        System.out.println("Iniciando processo de reconciliação...");
        // Obtém o timestamp da última mensagem local
        long lastSeenTimestamp = wall.isEmpty() ? 0 : wall.get(wall.size() - 1).getTimestamp();

        // Tenta se conectar a um dos outros nós para obter as mensagens
        for (int targetPort : replicationPorts) {
            try (Socket socket = new Socket(HOST, targetPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                // Sinaliza que é uma conexão de reconciliação e envia o timestamp
                out.writeObject("RECONCILIATION_REQUEST");
                out.writeObject(lastSeenTimestamp);

                // Recebe as mensagens faltantes
                List<Message> missingMessages = (List<Message>) in.readObject();
                wall.addAll(missingMessages);
                System.out.println("Reconciliação bem-sucedida! Adicionadas " + missingMessages.size() + " mensagens ao mural.");
                return; // Reconciliado com sucesso, pode sair do loop
            } catch (ConnectException e) {
                System.err.println("Não foi possível conectar ao nó na porta " + targetPort + ". Tentando o próximo...");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro na reconciliação com o nó na porta " + targetPort + ": " + e.getMessage());
            }
        }
        System.err.println("Falha na reconciliação. Nenhum nó disponível para sincronizar.");
    }

    public void stopServer() {
        this.isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar o servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java BulletinBoardNode <porta>");
            return;
        }

        int myPort = Integer.parseInt(args[0]);
        List<Integer> allPorts = List.of(65432, 65433, 65434);
        List<Integer> otherPorts = new ArrayList<>(allPorts);
        otherPorts.remove(Integer.valueOf(myPort));

        BulletinBoardNode node = new BulletinBoardNode(myPort, otherPorts);

        // Inicia a thread do servidor
        Thread serverThread = new Thread(node::startServer);
        serverThread.start();

        // Adicione um Scanner para ler comandos do console e simular falhas, etc.
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Comandos do nó ('exit', 'reconcile'): ");
            String command = scanner.nextLine();
            if ("exit".equalsIgnoreCase(command)) {
                node.stopServer();
                System.out.println("Nó encerrado.");
                break;
            } else if ("reconcile".equalsIgnoreCase(command)) {
                node.reconcile();
            }
        }
        scanner.close();
    }
}