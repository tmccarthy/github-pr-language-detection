package au.id.tmm.githubprlanguagedetection.github.model

import java.net.URI

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
