package org.w3.ircbot

import java.net.{ Socket, InetSocketAddress }
import java.io._
import java.util.concurrent.{ Executors, Callable }
import akka.io._
import akka.util.ByteString
import akka.actor.{ IO => _, _ }
import javax.net.ssl._
import akka.event.NoLogging
import javax.net.ssl._
import java.security._

/**
 * Companion object for IrcClient
 * defines a set of constants
 */
object IrcClient {

  // :betehess!bertails@66.31.43.2 PRIVMSG #spartacusse :salut sparta
  // :betehess!bertails@mcclure.w3.org PRIVMSG #mytest :toto
//  val PRIVMSG = """^:(\w+)!(.+?)@([^ ]+) PRIVMSG ([\w#&]+) :(.*)$""".r
  // :irc.w3.org 353 sparta-test = #spartacusse :sparta-test johann @betehess
  val JOINMSG = """^:([^ ]+) 353 ([^ ]+) = ([\w#&]+) :(.*)$""".r
  // :johann!johann@98.239.3.24 INVITE test :#spartacusse
  // :betehess!bertails@public.cloak INVITE pingpong #mouettes
  val INVITEMSG = """^:(\w+)!(.+?)@([^ ]+) INVITE ([^ ]+) (.*)$""".r
  // :test!test@98.239.3.24 PART #spartacusse
  val PARTMSG = """^:(\w+)!(.+?)@([^ ]+) PART ([\w#&]+) (.*)$""".r
  // :irc.w3.org 001 johann :Welcome to the W3C IRC Network johann
  val CONNUPMSG_R = """^:([^ ]+) 001 ([^ ]+) :(.*)$""".r
  // :johann!johann@98.239.3.24 KICK #sico test :va t en
  val KICKMSG_R = """^:(\w+)!(.+?)@([^ ]+) KICK ([^ ]+) ([^ ]+) :(.*)$""".r


  /**
   * https://github.com/spray/spray/blob/master/spray-io-tests/src/test/scala/spray/io/SslTlsSupportSpec.scala
   * http://publib.boulder.ibm.com/infocenter/javasdk/v6r0/index.jsp?topic=%2Fcom.ibm.java.security.component.doc%2Fsecurity-component%2Fjsse2Docs%2Fssltlsdata.html
   */
  def createSslEngine(remote: InetSocketAddress, keyStoreResource: String, password: String): SSLEngine = {
    def createSslContext(keyStoreResource: String, password: String): SSLContext = {
      val keyStore = KeyStore.getInstance("jks")
      keyStore.load(new FileInputStream(keyStoreResource), password.toCharArray)
      val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(keyStore, password.toCharArray)
      val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
      trustManagerFactory.init(keyStore)
      val context = SSLContext.getInstance("TLS")
      context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
      context
    }
    val context = createSslContext(keyStoreResource, password)
    val engine = context.createSSLEngine(remote.getHostName, remote.getPort)
    engine.setUseClientMode(true)
    engine
  }

  // just for debugging purposes
  val logger = new akka.event.LoggingAdapter {
    val isDebugEnabled: Boolean = true
    val isErrorEnabled: Boolean = true
    val isInfoEnabled: Boolean = true
    val isWarningEnabled: Boolean = true
    protected def notifyDebug(message: String): Unit = println("1 "+message)
    protected def notifyError(cause: Throwable,message: String): Unit = println("2 "+message)
    protected def notifyError(message: String): Unit = println("3 "+message)
    protected def notifyInfo(message: String): Unit = println("4 "+message)
    protected def notifyWarning(message: String): Unit = println("5 "+message)
  }

}

class AkkaSslHandler(init: TcpPipelineHandler.Init[TcpPipelineHandler.WithinActorContext, String, String], bot: IrcBot)
extends Actor with ActorLogging {

  import IrcClient._
  import Tcp._
  import context.system

  var pipeline: ActorRef = _

  def send(message: String): Unit = {
    println(s">> $message")
    pipeline ! init.Command(s"$message\r\n")
  }

  def receive = {

    case p: ActorRef =>
      println("got pipeline: " + p)
      pipeline = p
      // 
      send(s"NICK ${bot.nick.nickname}")
      send(s"USER ${bot.nick.nickname} 8 *  : ${bot.name}")
      bot.onEvent(self)(org.w3.ircbot.Connected)

    case CommandFailed(w: Write) => // O/S buffer was full

    case _: ConnectionClosed =>
      println("asked to close")
      context.stop(self)

    case init.Event(IrcEvent(event)) =>
      // side-effects
      event match {
        case Disconnected =>
          bot.onEvent(self)(Disconnected)
          context.stop(self)

        case PING(message) =>
          send("PONG :" + message)

        case _ => ()
      }
      // pass the event to the bot
      bot.onEvent(self)(event)

    // :irc.w3.org 353 spartabot = #spartacusse :spartabot johann betehess
    case init.Event(m: String)  if (JOINMSG findFirstMatchIn m).isDefined =>
      val hit = (JOINMSG findFirstMatchIn m).get
      // TODO use regex here
      val chunks = (m split " ").toList
      val channel = Channel(hit.group(3))
      val participants = (chunks drop 6) map (Nick(_))
      bot.onEvent(self)(RPL_NAMREPLY(channel, participants))

    case init.Event(m: String) if (CONNUPMSG_R findFirstMatchIn m).isDefined =>
      val hit = (CONNUPMSG_R findFirstMatchIn m).get
      val host = hit.group(1)
      val nick = Nick(hit.group(2))
      val message = hit.group(3)
      bot.onEvent(self)(CONNUPMSG(host, nick, message))

    case init.Event(m: String) if (KICKMSG_R findFirstMatchIn m).isDefined =>
      val hit = (KICKMSG_R findFirstMatchIn m).get
      val sender = User(Nick(hit.group(1)), Name(hit.group(2)), Host(hit.group(3)))
      val nick = Nick(hit.group(5))
      val channel = Channel(hit.group(4))
      val reason = hit.group(6)
      bot.onEvent(self)(KICKMSG(sender, channel, nick, reason))

    case CmdQuit(reason) =>
      send(s"QUIT :${reason}")
      sender ! Close

    case cmd: IrcCommand =>
      send(cmd.toString)

    case init.Event(foo) => println("^^ "+foo)

  }

}



/** IrcClient connects to a server, treats and delivers messages from/to the server
  * see: http://oreilly.com/pub/h/1963
  * see: http://irchelp.org/irchelp/rfc/rfc.html
  */
class IrcClient(bot: IrcBot) extends Actor with ActorLogging {

  import IrcClient._
  import Tcp._
  import context.system

  // establishes the TCP connection
  IO(Tcp) ! Connect(bot.remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      context stop self
 
    case c @ Connected(remote, local) =>
      // http://doc.akka.io/docs/akka/snapshot/scala/io-tcp.html
      val sslEngine = createSslEngine(remote, "/etc/ssl/certs/java/cacerts", "changeit")

      val init = TcpPipelineHandler.withLogger(
        logger, // NoLogging,
        new StringByteStringAdapter("utf-8") >>
          new DelimiterFraming(maxSize = 1024, delimiter = ByteString("\r\n"), includeDelimiter = false) >>
          new TcpReadWriteAdapter// >>
//          new SslTlsSupport(sslEngine)
      )
      val connection = sender

      val handler = context.actorOf(Props(classOf[AkkaSslHandler], init, bot))
      val pipeline = context.actorOf(TcpPipelineHandler.props(
        init, connection, handler))
      connection ! Register(pipeline)
      handler ! pipeline

    case foo => println("@@ "+foo)

  }

}
