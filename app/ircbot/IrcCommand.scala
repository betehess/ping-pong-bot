package org.w3.ircbot

sealed trait IrcCommand

case class CmdQuit(reason: String) extends IrcCommand {
  override def toString: String = s"QUIT :${reason}"
}

case class CmdJoin(channel: Channel) extends IrcCommand {
  override def toString: String = s"JOIN ${channel.name}"
}

case class CmdInvite(nick: Nick, channel: Channel) extends IrcCommand {
  override def toString: String = s"INVITE ${nick.nickname} ${channel.name}"
}

case class CmdPart(channel: Channel, reason: String) extends IrcCommand {
  override def toString: String = s"PART ${channel.name} :${reason}"
}

case class CmdKick(nick: Nick, channel: Channel, reason: String) extends IrcCommand {
  override def toString: String = s"KICK ${channel.name} ${nick.nickname} :%{reason}"
}

case class CmdSay(channel: Channel, message: String) extends IrcCommand {
  override def toString: String = s"PRIVMSG ${channel.name} :${message}"
}
