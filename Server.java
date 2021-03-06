import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.Map.Entry;

public class Server{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetEncoder encoder = charset.newEncoder();
  static private final CharsetDecoder decoder = charset.newDecoder();

  static private HashMap<String, ArrayList<SocketChannel>> rooms = new HashMap<String, ArrayList<SocketChannel>>();
  static private HashMap<SocketChannel, String> users = new HashMap<SocketChannel, String>();
  static private HashMap<SocketChannel, String> userStatus = new HashMap<SocketChannel, String>();
  static private HashMap<SocketChannel, String> usersRoom = new HashMap<SocketChannel, String>();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port);

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
          System.out.println( "Got connection from "+s);

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
          SocketChannel sc = s.getChannel();
          sc.configureBlocking( false );

            // Register it with the selector, for reading
          sc.register( selector, SelectionKey.OP_READ );

        } else if ((key.readyOps() & SelectionKey.OP_READ) ==
          SelectionKey.OP_READ) {

          SocketChannel sc = null;

          try {

              // It's incoming data on a connection -- process it
            sc = (SocketChannel)key.channel();
            boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
            if (!ok) {
              key.cancel();

              Socket s = null;
              try {
                s = sc.socket();
                System.out.println( "Closing connection to "+s);
                s.close();
              } catch( IOException ie ) {
                System.err.println( "Error closing socket "+s+": "+ie);
              }
            }

          } catch( IOException ie ) {

              // On exception, remove this channel from the selector
            key.cancel();

            try {
              sc.close();
            } catch( IOException ie2 ) { System.out.println( ie2 ); }

            System.out.println( "Closed "+sc );
          }
        }
      }

        // We remove the selected keys, because we've dealt with them.
      keys.clear();
    }
  } catch( IOException ie ) {
    System.err.println( ie );
  }
}


  // Just read the message from the socket and send it to stdout
static private boolean processInput( SocketChannel sc ) throws IOException {
    // Read the message to the buffer

  userStatus.put(sc,"init"); //utilizador esta no servidor mas nao tem nome associado

  buffer.clear();
  sc.read( buffer );
  buffer.flip();

    // If no data, close the connection
  if (buffer.limit()==0) {
    return false;
  }

    // Reenvia a mensagem recebida para o cliente F5.c
  //sc.write(buffer);
  //buffer.flip();

    // Decode and print the message to stdout
  String message = decoder.decode(buffer).toString();
    //System.out.println( message );

  String[] parts = message.split(" ");

  //System.out.println(parts[0]);

  if(parts[0].equals("/nick")){
    nick(sc,parts[1]);
  }

  else if(parts[0].equals("/priv")){
    String toSend = parts[2];
    for (int i=3; i<parts.length; i++) {
      toSend += " " + parts[i];
    }
    priv(sc,parts[1], toSend);
  }

  else if(parts[0].equals("/join")){
    join(sc,parts[1]);
  }

  else if(parts[0].equals("/leave")){
    leave(sc,false);
  }

  else if(parts[0].equals("/bye")){
    bye(sc);
  }

  else {
    message(sc, message);
  }

  return true;
}


static private void nick(SocketChannel sc, String nick) throws IOException {
    //System.out.println(nick);
    if(users.containsValue(nick)) // Se o nick já existe
    send(sc, "ERROR\n");
    else {
      String oldnick = users.get(sc);
      users.put(sc, nick);  // Regista o nickname (substitui para o novo nick se necessário)
      userStatus.put(sc,"outside"); //o utilizador ja tem nome mas nao emsta em nenhuma sala
      // Se o utilizador já estiver num room
      if(usersRoom.containsKey(sc)) {
        // Enviar mensagem para os outros utilizadores do room
        sendToOthers(sc, rooms.get(usersRoom.get(sc)), "NEWNICK " + oldnick + " " + nick +"\n");
      }
      System.out.println(nick + " has connected to the server.");
      send(sc, "OK\n");   // Enviar para o cliente
    }
  }

  static private void join(SocketChannel sc, String room) throws IOException {
    String user = users.get(sc);

    if(user == null) {
      send(sc, "ERROR\n");
      return;
    }

    if(usersRoom.containsKey(sc)) // Se o utilizador já estiver num room
      leave(sc, true);      // sair desse room

    if(rooms.containsKey(room)) { // Se o room já foi criado
      if(!rooms.get(room).contains(sc)) { // Se o utilizador ainda não está no room
        rooms.get(room).add(sc);    // é adicionado ao room
        userStatus.put(sc,"inside");
        usersRoom.put(sc, room);    // associa o utilizador com o room

        // Enviar mensagem para os outros utilizadores do room
        sendToOthers(sc, rooms.get(room), "JOINED " + user + "\n");
      }
    }
    else {    // Se o room ainda não foi criado
      ArrayList<SocketChannel> roomList = new ArrayList<SocketChannel>();
      roomList.add(sc);
      rooms.put(room, roomList);
      usersRoom.put(sc, room);
      userStatus.put(sc,"inside");
    }

    send(sc, "OK\n");
  }

  static private void leave(SocketChannel sc, boolean notif) throws IOException {
    if(!usersRoom.containsKey(sc)) {
      send(sc, "ERROR\n");
      return;
    }

    String user = users.get(sc);
    String room = usersRoom.get(sc);
    rooms.get(room).remove(sc);
    usersRoom.remove(sc);

    sendToOthers(sc, rooms.get(room), "LEFT " + user + "\n");

    if(!notif)
      send(sc, "OK\n");
  }

  static private void bye(SocketChannel sc) throws IOException {
    String user = users.get(sc);
    String room = usersRoom.get(sc);

    if(usersRoom.containsKey(sc)){
      leave(sc, true);
    }

    send(sc, "BYE\n");
    userStatus.put(sc,"outside");
    users.remove(sc);
    sc.close();
  }

  static private void message(SocketChannel sc, String message) throws IOException {
    if(!usersRoom.containsKey(sc)) {
      send(sc, "ERROR\n");
      return;
    }

    if(message.charAt(0) == '/')
      message = message.substring(1);

    String user = users.get(sc);
    String room = usersRoom.get(sc);

    // Enviar mensagem para todos os utilizadores do room
    sendToList(rooms.get(room), "MESSAGE " + user + " " + message + "\n");
  }

  static private void priv(SocketChannel sc, String sendTo, String message) throws IOException {
    String user = users.get(sc);

    for(Entry<SocketChannel, String> entry : users.entrySet())
      if(entry.getValue().equals(sendTo))
        send(entry.getKey(), "PRIVATE " + user + ' ' + message + "\n");  // Enviar a mensagem privada para o destinatário

      send(sc, "OK\n");
    }


    static private void sendToList(ArrayList<SocketChannel> list, String message) throws IOException {
      for(SocketChannel sc : list)
        send(sc, message);
    }

    static private void send(SocketChannel sc, String message) throws IOException {
      sc.write(encoder.encode(CharBuffer.wrap(message)));
    }

    static private void sendToOthers(SocketChannel user, ArrayList<SocketChannel> list, String message) throws IOException {
      for(SocketChannel sc : list)
        if(user != sc)
          send(sc, message);
      }

    }
