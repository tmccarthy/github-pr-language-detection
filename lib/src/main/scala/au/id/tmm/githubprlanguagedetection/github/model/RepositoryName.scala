package au.id.tmm.githubprlanguagedetection.github.model

final case class RepositoryName(owner: String, repo: String) {
  def asString: String = s"$owner/$repo"
}
