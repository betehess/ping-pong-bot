import org.w3.ircbot._

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Mode._

import akka.actor._
import java.net.InetSocketAddress

object Global extends GlobalSettings {

  val bot: IrcBot = new IrcBot {
    //    val remote = new InetSocketAddress("irc.w3.org", 6697)
    val remote = new InetSocketAddress("irc.w3.org", 6667)
//    val remote = new InetSocketAddress("www.w3.org", 443)
    val nick = Nick("pingpong")
    val name = "Ping Pong bot"
    def onEvent(ircClient: ActorRef) = {
      case Connected =>
        ircClient ! CmdJoin(Channel("#pingpong"))
      case PRIVMSG(user, channel, message) if message.startsWith(nick.nickname) =>
        val resp = s"${user.nick}, you just told me: ${message.substring(nick.nickname.size + 2)}"
        ircClient ! CmdSay(channel, resp)
      case PRIVMSG(user, channel, message) =>
        ircClient ! CmdSay(channel, s"${user.nick}, you just said: ${message}")
      case INVITE(_, _, channel) =>
        ircClient ! CmdJoin(channel)
      case m =>
        println(s"== $m")
    }
  }

  val system = ActorSystem("irc")

  override def onStart(app: Application): Unit = {
    system.actorOf(Props(classOf[IrcClient], bot))
  }
  
  override def onStop(app: Application): Unit = {
    system.shutdown()
  }

}
