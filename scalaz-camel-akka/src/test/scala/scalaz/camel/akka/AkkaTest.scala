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
package scalaz.camel.akka

import org.scalatest.{WordSpec, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.MustMatchers

import akka.actor.Actor
import akka.camel.{Message => Msg}

import scalaz._
import scalaz.concurrent.Strategy

import scalaz.camel.core._

/**
 * @author Martin Krasser
 */
trait AkkaTest extends AkkaTestContext with WordSpec with MustMatchers with BeforeAndAfterAll {
  import Scalaz._

  override def beforeAll = router.start
  override def afterAll = router.stop

  def support = afterWord("support")

  "scalaz.camel.akka.Akka" should support {
    "1:1 in-out messaging with an actor that is accessed via the native API" in {
      val appender = Actor.actorOf(new AppendReplyActor("-2", 1))
      val route = appendToBody("-1") >=> to(appender.manage) >=> appendToBody("-3")
      route responseFor Message("a") match {
        case Success(Message(body, _)) => body must equal("a-1-2-3")
        case _                         => fail("unexpected response")
      }
    }

    "1:n in-out messaging with an actor that is accessed via the native API" in {
      val appender = Actor.actorOf(new AppendReplyActor("-2", 3))
      val route = appendToBody("-1") >=> to(appender.manage) >=> appendToBody("-3")
      val queue = route responseQueueFor (Message("a"))
      List(queue.take, queue.take, queue.take) foreach { e =>
        e match {
          case Success(Message(body, _)) => body must equal("a-1-2-3")
          case _                         => fail("unexpected response")
        }
      }
    }

    "1:1 in-out messaging with an actor that is accesses via the Camel actor component" in {
      val greeter = Actor.actorOf(new GreetReplyActor)
      val route = appendToBody("-1") >=> to(greeter.manage.uri) >=> appendToBody("-3")
      route responseFor Message("a") match {
        case Success(Message(body, _)) => body must equal("a-1-hello-3")
        case _                         => fail("unexpected response")
      }

    }
  }

  class AppendReplyActor(s: String, c: Int) extends Actor {
    def receive = {
      case m: Message => for (_ <- 1 to c) self.reply(m.appendToBody(s))
    }
  }

  class GreetReplyActor extends Actor {
    def receive = {
      case Msg(body, _) => self.reply("%s-hello" format body)
    }
  }
}

class AkkaTestSequential extends AkkaTest
class AkkaTestConcurrent extends AkkaTest {
  dispatchConcurrencyStrategy = Strategy.Naive
  multicastConcurrencyStrategy = Strategy.Naive
}
