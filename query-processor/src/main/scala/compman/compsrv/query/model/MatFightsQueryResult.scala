package compman.compsrv.query.model

import compservice.model.protobuf.model.{Competitor, FightDescription, MatState}

case class MatFightsQueryResult (
                                  competitors: List[Competitor],
                                  fights: List[FightDescription]
                                )

case class MatsQueryResult (
                                  competitors: List[Competitor],
                                  mats: List[MatState]
                                )
