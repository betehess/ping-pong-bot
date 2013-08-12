package org.w3.ircbot

/* http://tools.ietf.org/html/rfc2812
 * http://tools.ietf.org/html/rfc2813
 */
sealed trait IrcEvent

object IrcEvent {

  /** see: http://mybuddymichael.com/writings/a-regular-expression-for-irc-messages.html
    *  :<prefix> <command> <params> :<trailing>
    */
  val r = """^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$""".r

  // <channel> [^ ]+ followed by space

  def unapply(s: String): Option[IrcEvent] = s match {
    case m if m.startsWith("ERROR :Closing Link") => Some(Disconnected)

    case r(null, "PING", null, message) => Some(PING(message))

    case r(User(sender), "PRIVMSG", channel, message) =>
      Some(PRIVMSG(sender, Channel(channel), message))

    case r(_, "353", RPL_NAMREPLY.r(channelType, channel), nicknames) =>
      val participants: Seq[Nick] =
        nicknames.split(" ").toSeq.map(nickname => Nick(nickname.replaceFirst("^@|\\+", "")))
      Some(RPL_NAMREPLY(Channel(channel), participants))

    case r(User(sender), "INVITE", params, null) =>
      params.split(" ") match {
        case Array(nickname, channel) => Some(INVITE(sender, Nick(nickname), Channel(channel)))
        case _ => None
      }

    case _ => None
  }

}

case object Connected extends IrcEvent

case object Disconnected extends IrcEvent

case class PING(message: String) extends IrcEvent

case class PRIVMSG(user: User, channel: Channel, message: String) extends IrcEvent

case class RPL_NAMREPLY(channel: Channel, participants: Seq[Nick]) extends IrcEvent

object RPL_NAMREPLY {
  /*
   *   353    RPL_NAMREPLY
   *          "( "=" / "*" / "@" ) <channel>
   *           :[ "@" / "+" ] <nick> *( " " [ "@" / "+" ] <nick> )
   *     - "@" is used for secret channels, "*" for private
   *       channels, and "=" for others (public channels).
   */
  val r = """^[^=\*@]*([=\*@]) (.+)$""".r
}

case class INVITE(sender: User, nick: Nick, channel: Channel) extends IrcEvent

case class CONNUPMSG(host: String, nick: Nick, message: String) extends IrcEvent

case class KICKMSG(user: User, channel: Channel, nick: Nick, reason: String) extends IrcEvent
