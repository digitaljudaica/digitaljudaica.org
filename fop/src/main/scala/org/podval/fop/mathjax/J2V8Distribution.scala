package org.podval.fop.mathjax

import org.podval.fop.util.{Architecture, Os}

final class J2V8Distribution(
  os: Os,
  arch: Architecture
) {
  override def toString: String = s"J2V8 for $os on $arch"

  val version: Option[String] = os match {
    case Os.Windows | Os.Mac => Some("4.6.0")
    // Note: native library needs to be compatible with the Java code used by the plugin (see build.gradle),
    // so it should probably be 4.6.0 even for Linux, but version of Node in it doesn't work with mathjax-node:
    // mathjax-node/lib/main.js:163: SyntaxError:
    //   Block-scoped declarations (let, const, function, class) not yet supported outside strict mode
    //   for (let key in paths) {
    // Conclusion: I have to use 4.8.0 on Linux and in build.gradle, so this probably won't work on any other platform :(
    case Os.Linux => Some("4.8.0")
    case _ => None
  }

  val osName: String = os match {
    case Os.Windows => "win32"
    case Os.Mac => "macosx"
    case Os.Linux => "linux"
    case _ => throw new IllegalArgumentException
  }

  val archName: String = arch match {
    case Architecture.i686 => "x86"
    case Architecture.x86_64 => "x86_64"
    case Architecture.amd64 => "x86_64"
    case _ => throw new IllegalArgumentException
  }

  def dependencyNotation: String =
    s"com.eclipsesource.j2v8:j2v8_${osName}_$archName:${version.get}"

  def libraryName: String =
    s"libj2v8_${osName}_$archName.${os.libraryExtension}"
}
