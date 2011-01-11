/*
 * Copyright 2010-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scalaz.camel

import org.apache.camel.{ExchangePattern, CamelContext, Message => CamelMessage}

/**
 * An immutable representation of a Camel message.
 *
 * @author Martin Krasser
 */
case class Message(body: Any, headers: Map[String, Any] = Map.empty) {

  // TODO: make Message a parameterized type
  // TODO: make Message an instance of Functor

  val ExceptionHeader = "scalaz.camel.exception"
  val exchange = MessageExchange(false)

  override def toString = "Message: %s" format body

  def setBody(body: Any) = Message(body, headers, exchange)

  def setHeaders(headers: Map[String, Any]) = Message(body, headers, exchange)

  def addHeaders(headers: Map[String, Any]) = Message(body, this.headers ++ headers, exchange)

  def addHeader(header: (String, Any)) = Message(body, headers + header, exchange)

  def removeHeader(headerName: String) = Message(body, headers - headerName, exchange)

  def exception: Option[Exception] = header(ExceptionHeader).asInstanceOf[Option[Exception]]

  def setException(e: Exception) = addHeader(ExceptionHeader, e)

  def exceptionHandled = removeHeader(ExceptionHeader)

  def headers(names: Set[String]): Map[String, Any] = headers.filter(names contains _._1)

  def header(name: String): Option[Any] = headers.get(name)

  def headerAs[A](name: String)(implicit m: Manifest[A], mgnt: ContextMgnt): Option[A] =
    header(name).map(convertTo[A](m.erasure.asInstanceOf[Class[A]], mgnt.context) _)

  def bodyAs[A](implicit m: Manifest[A], mgnt: ContextMgnt): A =
    convertTo[A](m.erasure.asInstanceOf[Class[A]], mgnt.context)(body)

  // TODO: remove once Message is a Functor
  def bodyTo[A](implicit m: Manifest[A], mgnt: ContextMgnt): Message =
    Message(convertTo[A](m.erasure.asInstanceOf[Class[A]], mgnt.context)(body), headers, exchange)

  // TODO: remove once Message is a Functor
  def appendBody(body: Any)(implicit mgnt: ContextMgnt) =
    setBody(bodyAs[String] + convertTo[String](classOf[String], mgnt.context)(body))

  // TODO: remove once Message is a Functor
  def transformBody[A](transformer: A => Any) =
    setBody(transformer(body.asInstanceOf[A]))

  private[camel] def setExchange(exch: MessageExchange) =
    Message(body, headers, exch)

  private[camel] def setExchange(m: Message) =
    Message(body, headers, m.exchange)

  private[camel] def setOneway(oneway: Boolean) =
    Message(body, headers, MessageExchange(oneway))

  private def convertTo[A](c: Class[A], context: CamelContext)(a: Any): A =
    context.getTypeConverter.mandatoryConvertTo[A](c, a)

}

/**
 * @author Martin Krasser
 */
object Message {
  /** Creates a Converter from a Camel message */
  implicit def camelMessageToConverter(cm: CamelMessage): MessageConverter = new MessageConverter(cm)

  /** Create a Message with body, headers and a Camel exchange */
  private[camel] def apply(body: Any, headers: Map[String, Any], exch: MessageExchange): Message = new Message(body, headers) {
    override val exchange = exch
  }
}

/**
 * @author Martin Krasser
 */
case class MessageExchange(oneway: Boolean)

/**
 * Converts between <code>scalaz.camel.Message</code> and
 * <code>org.apache.camel.Message</code>.
 *
 * @author Martin Krasser
 */
class MessageConverter(val cm: CamelMessage) {
  import scala.collection.JavaConversions._

  def fromMessage(m: Message): CamelMessage = {
    cm.getExchange.setPattern(cePattern(m.exchange))
    cm.setBody(m.body)
    for (h <- m.headers) cm.getHeaders.put(h._1, h._2.asInstanceOf[AnyRef])
    cm
  }

  def toMessage: Message = toMessage(Map.empty)
  def toMessage(headers: Map[String, Any]): Message =
    Message(cm.getBody, cmHeaders(headers, cm), MessageExchange(isOneway(cm)))

  private def cmHeaders(headers: Map[String, Any], cm: CamelMessage) = headers ++ cm.getHeaders
  private def cePattern(me: MessageExchange) = if (me.oneway) ExchangePattern.InOnly else ExchangePattern.InOut
  private def isOneway(cm: CamelMessage) = if (cm.getExchange.getPattern.isOutCapable) false else true
}

