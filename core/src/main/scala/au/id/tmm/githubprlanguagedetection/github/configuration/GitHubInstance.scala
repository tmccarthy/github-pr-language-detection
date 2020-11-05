package au.id.tmm.githubprlanguagedetection.github.configuration

import java.net.URI

sealed trait GitHubInstance

object GitHubInstance {
  case object GitHubDotCom                     extends GitHubInstance
  final case class GitHubEnterprise(base: URI) extends GitHubInstance
}
