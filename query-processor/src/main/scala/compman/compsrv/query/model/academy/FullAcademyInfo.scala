package compman.compsrv.query.model.academy

case class FullAcademyInfo(id: String, name: Option[String], coaches: Option[Set[String]])
