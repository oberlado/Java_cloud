import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class TelnetTerm {


    private Path currentDir;
    private final Path serverDir;
    private final ByteBuffer buffer;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public TelnetTerm() throws IOException {
        buffer = ByteBuffer.allocate(100);
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(8188));
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        log.debug("Server started");
        serverDir = Paths.get("serverdir");
        if (!Files.exists(serverDir)) {
            Files.createDirectory(serverDir);
        }
        currentDir = serverDir;


        while (serverSocketChannel.isOpen()) {
            selector.select();


            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    handleAcceptKey();

                }
                if (key.isReadable()) {
                    handleReadKey(key);

                }

                iterator.remove();
            }

        }
    }

    private void handleReadKey(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();
        int r;
        while (true) {
            r = channel.read(buffer);
            if (r == -1) {
                channel.close();
                log.debug("client disconnect");
                return;
            }
            if (r == 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();
        }

        String[] str = sb.toString().trim().split(" ", 3);
        switch (str[0]) {
            case "ls":
                log.debug("ls");
                listFiles(channel);
                break;
            case "cat":
                printFile(str[1], channel);
                break;
            case "cd":
                switching(str, channel);
                break;
            case "mkdir":
                makeDir(str[1], channel);
                break;
            case "touch":
                createFile(str[1], channel);
                break;
            case "help":
                help(channel);
                break;
        }

    }

    @SneakyThrows
    private void switching(String[] str, SocketChannel channel) {
        if (str.length == 1) {
            currentDir = serverDir;
            channel.write(ByteBuffer.wrap(("Current directory is:" + currentDir.getFileName() +
                    System.lineSeparator() +
                    System.lineSeparator()).getBytes()));
            return;
        } else if (str.length == 2) {
            if (str[1].equals("..") && !currentDir.equals(serverDir)) {
                currentDir = currentDir.getParent();
                channel.write(ByteBuffer.wrap(("Current directory is:" + currentDir.getFileName() +
                        System.lineSeparator() +
                        System.lineSeparator()).getBytes()));
                return;
            }
            List<String> list = Files.list(currentDir).map(x -> x.getFileName().toString()).collect(Collectors.toList());
            for (String s : list) {
                if (str[1].equals(s) && Files.isDirectory(Paths.get(currentDir.toString(), str[1]))) {
                    currentDir = currentDir.resolve(Paths.get(str[1]));
                    channel.write(ByteBuffer.wrap(("Current directory is:" + currentDir.getFileName() +
                            System.lineSeparator() +
                            System.lineSeparator()).getBytes()));
                    return;
                }
            }
            channel.write(ByteBuffer.wrap(("Invalid directory : " + str[1] +
                    System.lineSeparator() +
                    System.lineSeparator()).getBytes()));
        }
    }

    @SneakyThrows
    private void createFile(String s, SocketChannel channel) {
        if (Files.exists(Paths.get(currentDir.toString(), s))) {
            channel.write(ByteBuffer.wrap(("File " + s + " is already exist" + System.lineSeparator() + System.lineSeparator()).getBytes()));
            return;
        }
        Path path = Files.createFile(Paths.get(currentDir.toString(), s));
        if (Files.exists(path)) {
            channel.write(ByteBuffer.wrap(("File " + s + " is create" + System.lineSeparator() + System.lineSeparator()).getBytes()));
        }
    }

    @SneakyThrows
    private void makeDir(String s, SocketChannel channel) {
        if (Files.exists(Paths.get(currentDir.toString(), s))) {
            channel.write(ByteBuffer.wrap(("Directory " + s + " already exist" + System.lineSeparator() + System.lineSeparator()).getBytes()));
            return;
        }
        Path path = Files.createDirectory(Paths.get(currentDir.toString(), s));
        if (Files.exists(path)) {
            channel.write(ByteBuffer.wrap(("Directory " + s + " is create" + System.lineSeparator() + System.lineSeparator()).getBytes()));
        }
    }

    @SneakyThrows
    private void printFile(String fileName, SocketChannel channel) {
        channel.write(ByteBuffer.wrap(Files.readAllBytes(Paths.get(currentDir.toString(), fileName))));
        channel.write(ByteBuffer.wrap(System.lineSeparator().getBytes()));
    }

    @SneakyThrows
    private void listFiles(SocketChannel channel) {
        log.debug("start list files");
        List<String> listFiles = Files.list(currentDir).map(x -> x.getFileName().toString()).collect(Collectors.toList());
        for (String listFile : listFiles) {
            if (Files.isDirectory(Paths.get(currentDir.toString(), listFile))) {
                channel.write(ByteBuffer.wrap((listFile + "/" + System.lineSeparator()).getBytes()));
            } else {
                channel.write(ByteBuffer.wrap((listFile + System.lineSeparator()).getBytes()));
            }
        }
    }

    @SneakyThrows
    private void help(SocketChannel channel) {
        StringBuilder sb = new StringBuilder();
        sb.append("ls - Список файлов в текущей директории");
        sb.append(System.lineSeparator());
        sb.append("cat (ИМЯ_ФАЙЛА) - прочитать содержимое файла");
        sb.append(System.lineSeparator());
        sb.append("mkdir (ИМЯ_ДИРЕКТОИИ) - создать новую директорию");
        sb.append(System.lineSeparator());
        sb.append("touch (ИМЯ_ФАЙЛА) - создать файл");
        sb.append(System.lineSeparator());
        sb.append("ls (ИМЯ_ДИРЕКТОРИИ) - переход в другую директорию (ls .. - переход на уровень выше, ls - переход в дефолтную папку)");
        sb.append(System.lineSeparator());
        channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
        System.out.println(sb);
    }

    private void handleAcceptKey() throws IOException {

        SocketChannel channel = serverSocketChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap(("Welcome" + System.lineSeparator()).getBytes()));
        log.debug("Client connected");
        help(channel);
    }
}
