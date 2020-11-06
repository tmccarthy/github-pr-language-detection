package au.id.tmm.githubprlanguagedetection.github.model

import java.net.URI

import scala.collection.immutable.ArraySeq

final case class Commit(
  repository: Option[Commit.Repository], // This is unknown if the repo has been deleted
  ref: Option[String],
  sha: ArraySeq[Byte],
)

object Commit {
  final case class Repository(
    cloneUris: Repository.CloneUris,
    name: RepositoryName,
  )

  object Repository {
    final case class CloneUris(
      ssh: String,
      https: URI,
    )
  }
}