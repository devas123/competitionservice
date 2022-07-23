package compman.compsrv.logic.actor.kafka

import org.apache.kafka.common.TopicPartition

case class StartOffsetsAndTopicEndOffset(
                                          startOffsets: Map[TopicPartition, Long],
                                          endOffsets: Map[TopicPartition, Long]
                                        )

