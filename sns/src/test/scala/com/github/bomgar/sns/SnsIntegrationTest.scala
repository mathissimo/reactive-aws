package com.github.bomgar.sns

import com.github.bomgar.sns.domain._
import com.github.bomgar.sns.testsupport.{WithTopicAndTestQueue, WithTopic}
import com.ning.http.client.AsyncHttpClientConfig.Builder
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.ws.ning.NingWSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import scala.concurrent.ExecutionContext.Implicits.global

class SnsIntegrationTest extends Specification with FutureAwaits with DefaultAwaitTimeout with AfterAll {

  val ningConfig = new Builder().build()
  val wsClient: NingWSClient = new NingWSClient(ningConfig)

  "A sns client" should {

    tag("integration")
    "create a new topic" in new WithTopic(wsClient) {
      testTopic.topicArn must endWith(topicName)
    }

    tag("integration")
    "list existing topics" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      //amazon needs some time to include it in the list
      Thread.sleep(2000)

      var topicReferenceListResult : TopicReferenceListResult = null
      var allPagedTopics = Seq.empty[TopicReference]
      var nextPageToken : Option[String] = None
      do {
        topicReferenceListResult = await(client.listTopics(nextPageToken))
        nextPageToken = topicReferenceListResult.nextPageToken
        allPagedTopics ++= topicReferenceListResult.topicReferences
      } while (nextPageToken.isDefined)

      allPagedTopics must contain(testTopic)
    }

    tag("integration")
    "delete existing topics" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      //amazon needs some time to include it in the list
      Thread.sleep(2000)

      await(client.deleteTopic(testTopic))
    }

    tag("integration")
    "get attributes for existing topic" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      //amazon needs some time to include it in the list
      Thread.sleep(2000)

      val topicAttributes = await(client.getTopicAttributes(testTopic))

      topicAttributes.topicArn must beSome (testTopic.topicArn)
    }

    tag("integration")
    "set attribute for existing topic" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      //amazon needs some time to include it in the list
      Thread.sleep(2000)
      val displayName: String = "dufte"

      await(client.setTopicAttribute(testTopic, "DisplayName", displayName))

      val topicAttributes = await(client.getTopicAttributes(testTopic))
      topicAttributes.displayName must beSome (displayName)
    }

    tag("integration")
    "publish a message" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      //amazon needs some time to include it in the list
      Thread.sleep(2000)

      await(client.publish("TestMessage",testTopic))
    }

    tag("integration")
    "list subscriptions by topic" in new WithTopicAndTestQueue(wsClient) {
      testTopic // create instance of lazy val
      Thread.sleep(2000)
      testQueueArn
      await(client.subscribe(testTopic, testQueueArn, "sqs" ))
      private val testEmail: String = "success@simulator.amazonses.com"
      await(client.subscribe(testTopic, testEmail, "email"))
      Thread.sleep(60000)

      var subscriptionListResult : SubscriptionListResult = null
      var allPagedSubscriptions = Seq.empty[Subscription]
      var nextPageToken : Option[String] = None
      do {
        subscriptionListResult = await(client.listSubscriptionsByTopics(testTopic, nextPageToken))
        nextPageToken = subscriptionListResult.nextPageToken
        allPagedSubscriptions ++= subscriptionListResult.subscriptions
      } while (nextPageToken.isDefined)


      allPagedSubscriptions.length must equalTo(2)
      val endpoints = allPagedSubscriptions.map(_.endpoint)
      endpoints must contain(Some(testQueueArn))
      endpoints must contain(Some(testEmail))
    }

    tag("integration")
    "list subscriptions" in new WithTopicAndTestQueue(wsClient) {
      testTopic // create instance of lazy val
      Thread.sleep(2000)
      testQueueArn
      await(client.subscribe(testTopic, testQueueArn, "sqs" ))
      private val testEmail: String = "success@simulator.amazonses.com"
      await(client.subscribe(testTopic, testEmail, "email"))
      Thread.sleep(60000)

      var subscriptionListResult : SubscriptionListResult = null
      var allPagedSubscriptions = Seq.empty[Subscription]
      var nextPageToken : Option[String] = None
      do {
        subscriptionListResult = await(client.listSubscriptions(nextPageToken))
        nextPageToken = subscriptionListResult.nextPageToken
        allPagedSubscriptions ++= subscriptionListResult.subscriptions
      } while (nextPageToken.isDefined)


      allPagedSubscriptions.length must beGreaterThanOrEqualTo(2)
      val endpoints = allPagedSubscriptions.map(_.endpoint)
      endpoints must contain(Some(testQueueArn))
      endpoints must contain(Some(testEmail))
    }

    tag("integration")
    "set permission for topic" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      Thread.sleep(2000)
      val awsId = Option(System.getenv("AWS_ID")).getOrElse(throw new IllegalArgumentException("Missing variable AWS_ID"))
      val permission = new TopicPermission(
        testTopic,
        "TestPermission",
        List("Publish"),
        List(awsId))

      await(client.addPermission(permission))
    }

    tag("integration")
    "remove permission for topic" in new WithTopic(wsClient) {
      testTopic // create instance of lazy val
      Thread.sleep(2000)
      val awsId = Option(System.getenv("AWS_ID")).getOrElse(throw new IllegalArgumentException("Missing variable AWS_ID"))
      val permission = new TopicPermission(
        testTopic,
        "TestPermission",
        List("Publish"),
        List(awsId))
      await(client.addPermission(permission))

      await(client.removePermission(permission))
    }

    tag("integration")
    "subscribe to a topic" in new WithTopicAndTestQueue(wsClient) {
      testTopic // create instance of lazy val
      Thread.sleep(2000)
      testQueueArn
      val subscriptionReference = await(client.subscribe(testTopic, testQueueArn, "sqs" ))

      subscriptionReference.confirmed must beTrue
      subscriptionReference.subscriptionArn must not beNone

      Thread.sleep(60000) // 13 Jan 2016: Subscription assigned reliable not before 60sec
      val topicAttributes = await(client.getTopicAttributes(testTopic))

      topicAttributes.subscriptionsConfirmed must beSome (1)
    }

  }

  override def afterAll() = wsClient.close()

}
