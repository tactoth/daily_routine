resolvers += Resolver.url(
  "bintray-alpeb-sbt-plugins",
  url("http://dl.bintray.com/alpeb/sbt-plugins"))(
  Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
