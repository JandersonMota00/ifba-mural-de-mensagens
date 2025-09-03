import java.io.*;
import java.net.*;
import java.util.Scanner;

public class BulletinBoardClient {
    private static final String HOST = "127.0.0.1";
    
    public static void main(String[] args) {
        // Verifica se a porta foi fornecida como argumento
        if (args.length != 1) {
            System.out.println("Uso: java BulletinBoardClient <porta_do_servidor>");
            return;
        }

        int serverPort = Integer.parseInt(args[0]);

        try (
            // Estabelece a conexão com o servidor
            Socket socket = new Socket(HOST, serverPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Conectado ao servidor na porta " + serverPort);

            // Envia o tipo de conexão para o servidor
            out.writeObject("CLIENT");

            // Recebe e imprime a mensagem de boas-vindas do servidor
            String welcomeMessage = (String) in.readObject();
            System.out.println(welcomeMessage);

            // Loop principal para enviar comandos e receber respostas
            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine();
                out.writeObject(command);

                if ("exit".equalsIgnoreCase(command.trim())) {
                    System.out.println("Saindo...");
                    break;
                }

                // Recebe e imprime a resposta do servidor
                String response = (String) in.readObject();
                System.out.println(response);
            }
        } catch (ConnectException e) {
            System.err.println("Erro: Não foi possível conectar ao servidor. Verifique se o nó está em execução na porta " + serverPort + ".");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro de comunicação com o servidor: " + e.getMessage());
        }
    }
}
