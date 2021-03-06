package uk.gov.justice.hmpps.offenderevents.resource

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.Awaitility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue
import uk.gov.justice.hmpps.offenderevents.subscribe.hmppsEventTestQueue
import uk.gov.justice.hmpps.offenderevents.subscribe.prisonEventTestQueue

abstract class QueueListenerIntegrationTest : IntegrationTestBase() {

  @SpyBean
  @Qualifier("awsSqsClient")
  protected lateinit var awsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("awsSqsDlqClient")
  protected lateinit var awsSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("testSqsClient")
  protected lateinit var testSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("testHmppsSqsClient")
  protected lateinit var testHmppsSqsClient: AmazonSQS

  @Autowired
  protected lateinit var sqsConfigProperties: SqsConfigProperties

  // The SQS clients for the production prisonEventsQueue
  fun getNumberOfMessagesCurrentlyOnQueue(): Int = awsSqsClient.numMessages(queueUrl)
  val queueName: String by lazy { sqsConfigProperties.prisonEventQueue().queueName }
  val queueUrl: String by lazy { awsSqsClient.getQueueUrl(queueName).queueUrl }
  fun getNumberOfMessagesCurrentlyOnDlq(): Int = awsSqsDlqClient.numMessages(dlqUrl)
  val dlqName: String by lazy { sqsConfigProperties.prisonEventQueue().dlqName }
  val dlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(dlqName).queueUrl }

  // The SQS clients for the test prisonEventTestQueue
  fun getNumberOfMessagesCurrentlyOnTestQueue(): Int = testSqsClient.numMessages(testQueueUrl)
  val testQueueName: String by lazy { sqsConfigProperties.prisonEventTestQueue().queueName }
  val testQueueUrl: String by lazy { testSqsClient.getQueueUrl(testQueueName).queueUrl }

  // The SQS clients for the test hmppsEventTestQueue
  fun getNumberOfMessagesCurrentlyOnHMPPSTestQueue(): Int = testHmppsSqsClient.numMessages(testHmppsQueueUrl)
  fun purgeQueues() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    Awaitility.await().until { getNumberOfMessagesCurrentlyOnQueue() == 0 }
    awsSqsDlqClient.purgeQueue(PurgeQueueRequest(dlqUrl))
    Awaitility.await().until { getNumberOfMessagesCurrentlyOnDlq() == 0 }
    testSqsClient.purgeQueue(PurgeQueueRequest(testQueueUrl))
    Awaitility.await().until { getNumberOfMessagesCurrentlyOnTestQueue() == 0 }
    testHmppsSqsClient.purgeQueue(PurgeQueueRequest(testHmppsQueueUrl))
    Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSTestQueue() == 0 }
  }

  val testHmppsQueueName: String by lazy { sqsConfigProperties.hmppsEventTestQueue().queueName }
  val testHmppsQueueUrl: String by lazy { testHmppsSqsClient.getQueueUrl(testHmppsQueueName).queueUrl }
}

fun AmazonSQS.numMessages(url: String): Int {
  val queueAttributes = getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))
  return queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
}
