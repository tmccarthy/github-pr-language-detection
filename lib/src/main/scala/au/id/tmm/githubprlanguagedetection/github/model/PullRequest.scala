package au.id.tmm.githubprlanguagedetection.github.model

import java.net.URI
import java.time.Instant

final case class PullRequest(
  repository: Repository,
  number: Int,
  whenCreated: Instant,
  whenClosed: Option[Instant],
  title: String,
  htmlUrl: URI,
  isOpen: Boolean,
  patch: URI,
  diff: URI,
  issue: URI,
  base: Commit,
  head: Commit,
  whenMerged: Option[Instant],
)
