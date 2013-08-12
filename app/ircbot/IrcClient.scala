package org.w3.ircbot

import java.net.{ Socket, InetSocketAddress }
import java.io.{ BufferedReader, PrintWriter, InputStreamReader, OutputStreamWriter }
import java.util.concurrent.{ Executors, Callable }
import akka.io.{ IO, Tcp, TcpPipelineHandler, SslTlsSupport }
import akka.util.ByteString

import akka.actor._

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
}



/** IrcClient connects to a server, treats and delivers messages from/to the server
  * see: http://oreilly.com/pub/h/1963
  * see: http://irchelp.org/irchelp/rfc/rfc.html
  */
class IrcClient(bot: IrcBot) extends Actor {

  import IrcClient._
  import Tcp._
  import context.system

  // established the TCP connection
  IO(Tcp) ! Connect(bot.remote)

  // used as a buffer to store what's sent by the server
  var bs: ByteString = ByteString.empty

  /** extracts lines from  */
  def readLines(): Iterable[String] = {
    val builder = Iterable.newBuilder[String]
    @annotation.tailrec
    def getLines(): Unit = {
      val index = bs.indexOf('\r')
      if (index >= 0  &&  index < bs.size - 1  &&  bs(index + 1) == '\n') {
        val line = bs.slice(0, index).utf8String
        println(s"<< ${line}")
        builder += line
        bs = bs.drop(index + 2)
        getLines()
      } else ()
    }
    getLines()
    builder.result()
  }

  def receive = {
    case CommandFailed(_: Connect) =>
      context stop self
 
    case c @ Connected(remote, local) =>
      val connection = sender
      // http://doc.akka.io/docs/akka/snapshot/scala/io-tcp.html
//      val init = TcpPipelineHandler.props(
//        new SslTlsSupport(sslEngine(remote, client = false)),
//        connection,
//        self
//      )
//      val pipeline = context.actorOf(init)
      connection ! Register(self)
      def send(message: String): Unit = {
        println(s">> $message")
        connection ! Write(ByteString(message + "\r\n"))
      }
      send(s"NICK ${bot.nick.nickname}")
      send(s"USER ${bot.nick.nickname} 8 *  : ${bot.name}")
      bot.onEvent(self)(org.w3.ircbot.Connected)
      // the new "receive" function after connection is established
      context become {
        case CommandFailed(w: Write) => // O/S buffer was full

        case Received(data) =>
          //println("received" + data.utf8String)
          bs = bs ++ data
          readLines() foreach { line =>
            self ! line
          }

        case _: ConnectionClosed => context.stop(self)

        case IrcEvent(event) =>
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
        case m: String  if (JOINMSG findFirstMatchIn m).isDefined =>
          val hit = (JOINMSG findFirstMatchIn m).get
          // TODO use regex here
          val chunks = (m split " ").toList
          val channel = Channel(hit.group(3))
          val participants = (chunks drop 6) map (Nick(_))
          bot.onEvent(self)(RPL_NAMREPLY(channel, participants))

        case m: String if (CONNUPMSG_R findFirstMatchIn m).isDefined =>
          val hit = (CONNUPMSG_R findFirstMatchIn m).get
          val host = hit.group(1)
          val nick = Nick(hit.group(2))
          val message = hit.group(3)
          bot.onEvent(self)(CONNUPMSG(host, nick, message))

        case m: String if (KICKMSG_R findFirstMatchIn m).isDefined =>
          val hit = (KICKMSG_R findFirstMatchIn m).get
          val sender = User(Nick(hit.group(1)), Name(hit.group(2)), Host(hit.group(3)))
          val nick = Nick(hit.group(5))
          val channel = Channel(hit.group(4))
          val reason = hit.group(6)
          bot.onEvent(self)(KICKMSG(sender, channel, nick, reason))

        case CmdQuit(reason) =>
          send(s"QUIT :${reason}")
          connection ! Close

        case cmd: IrcCommand =>
          send(cmd.toString)

      }
  }

}
