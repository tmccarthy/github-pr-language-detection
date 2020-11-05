package au.id.tmm.githubprlanguagedetection.github.configuration

sealed trait GitHubCredentials

object GitHubCredentials {

  case object Anonymous                                         extends GitHubCredentials
  final case class AccessToken(username: String, token: String) extends GitHubCredentials

}
