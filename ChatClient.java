import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
	JFrame frame = new JFrame("Chat Client");
	private JTextField chatBox = new JTextField();
	private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
	private SocketChannel socketChannel;


    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
	public void printMessage(final String message) {
		chatArea.append(message);
	}


    // Construtor
	public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.add(chatBox);
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.SOUTH);
		frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
		frame.setSize(500, 300);
		frame.setVisible(true);
		chatArea.setEditable(false);
		chatBox.setEditable(true);
		chatBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					newMessage(chatBox.getText());
				} catch (IOException ex) {
				} finally {
					chatBox.setText("");
				}
			}
		});
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
		socketChannel = SocketChannel.open();
		socketChannel.connect(new InetSocketAddress(server, port));
	}

	public void writeTextArea(String text) {
		chatArea.setText(chatArea.getText() + text);
	}


	public void newMessage(String message) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(16384);
		buf.clear();
		buf.put(message.getBytes());

		buf.flip();

		while(buf.hasRemaining()) {
			socketChannel.write(buf);
		}

	}

	public void run() throws IOException {
		int bytesRead = 0;
		while(bytesRead >= 0) {
			ByteBuffer buf = ByteBuffer.allocate(16384);

			bytesRead = socketChannel.read(buf);
			writeTextArea(new String(buf.array()));
			buf.clear();
		}
	}


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
	public static void main(String[] args) throws IOException {
		ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
		client.run();
	}

}
