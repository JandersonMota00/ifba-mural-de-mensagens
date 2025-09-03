import java.io.*;
import java.util.*;

// Classe para representar uma mensagem no mural
class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String author;
    private final String content;
    private final long timestamp;
    private final boolean isPrivate;

    public Message(String author, String content, boolean isPrivate) {
        this.author = author;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isPrivate = isPrivate;
    }

    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isPrivate() { return isPrivate; }

    @Override
    public String toString() {
        return "Autor: " + author + ", Mensagem: '" + content + "'";
    }

    // Essencial para o CopyOnWriteArrayList.contains() funcionar corretamente
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return timestamp == message.timestamp &&
               isPrivate == message.isPrivate &&
               author.equals(message.author) &&
               content.equals(message.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(author, content, timestamp, isPrivate);
    }
}