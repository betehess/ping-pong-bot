package org.w3.ircbot

import java.net.InetSocketAddress
import akka.actor._

/**
  * 
  */
trait IrcBot {

  def remote: InetSocketAddress

  def nick: Nick

  def name: String

  def onEvent(ircClient: ActorRef): PartialFunction[IrcEvent, Unit]

}

