/* sxr -- Scala X-Ray
 * Copyright 2009, 2010 Mark Harrah, Olivier Michallat
 */

package sxr

import java.io.File
import scala.collection.mutable.ArrayBuffer

object HtmlWriter
{
  /** The location to store the index relative to the output directory.*/
  val IndexRelativePath = "index.html"
  /** The location to store the style sheet relative to the output directory.*/
  val CSSRelativePath = "style.css"
  /** The location to store the script relative to the output directory.*/
  val JSRelativePath = "linked.js"
  /** The location to store jQuery relative to the output directory.*/
  val JQueryRelativePath = "jquery-all.js"

  val HtmlExtension = ".html"

  /** The path of the default style sheet resource.*/
  val DefaultCSS = "/default-style.css"
  /** The path of the default script resource.*/
  val LinkedJS = "/linked.js"
  /** The path of the JQuery resource.*/
  val LinkedJQuery = "/" + JQueryRelativePath

  /** Copies the default style sheet available as a resource on the classpath to the file 'to'.*/
  def writeDefaultCSS(to: File) { FileUtil.writeResource(DefaultCSS, to) }
  /** Copies the default script available as a resource on the classpath to the file 'to'.*/
  def writeJS(to: File) { FileUtil.writeResource(LinkedJS, to) }
  /** Copies the jQuery script available as a resource on the classpath to the file 'to'.*/
  def writeJQuery(to: File) { FileUtil.writeResource(LinkedJQuery, to) }

  case class PathAndLineCount(path:String,lineCount:Option[Int])

  final def header(title:String ) = Seq[Any](
     "<html>","<head>", <title>{title}</title>
    ,"""<meta http-equiv="Pragma" content="no-cache">"""
    ,"""<meta http-equiv="Cache-Control" content="no-store">"""
    ,"""<meta http-equiv="Cache-Control" content="no-cache">"""
    ,"""<meta http-equiv="Expires" content="-1">"""
    ,"</head>"
    ,"<body>"
  ).mkString("\n")

  def writeIndex(to: File, files: List[FileWithLineCount], dirs:List[File])
  {
    try{
      val relativizeAgainst = to.getParentFile

      def convertPath(f:File):Option[String] = {
        FileUtil.relativize(relativizeAgainst, f)
      }

      val fileList = {
        for{
          f <- files
          p <- convertPath(f.file).toList
        }yield PathAndLineCount(p,f.lineCount) 
      }.sortBy(_.path)

      val dirList = {
        for{
          f <- dirs
          p <- convertPath(f).toList
        }yield PathAndLineCount(p,None) 
      }.sortBy(_.path)

      FileUtil.withWriter(to) { out =>
        out.write(header(""))
        out.write("""<h2>source files</h2><ol>""")
        fileList.foreach(writeEntry(out))
        out.write("""</ol><h2>sub directories</h2>""")
        dirList.foreach(writeEntry(out))
        out.write("</body>\n</html>")
      }
    }catch{case e => e.printStackTrace}
  }

  import java.io.Writer
  private def writeEntry(out: Writer)(file: PathAndLineCount)
  {
    Iterator(
      """<li><a target="_blank" href=""""
     ,file.path
     ,"\">"
     ,{if(file.path.endsWith(".html"))
        file.path.substring(0, file.path.length - ".html".length)
      else
        file.path}
     ,"</a>"
     ,file.lineCount.getOrElse("").toString
     ,"</li>\n"
    ).foreach(out.write)
  }
}

/** Outputs a set of html files and auxiliary javascript and CSS files that annotate the source
  * code for display in a web browser. */
class HtmlWriter(context: OutputWriterContext) extends OutputWriter {

  val outputDirectory = context.outputDirectory
  val encoding = context.encoding

  import HtmlWriter._
  val info = new OutputInfo(outputDirectory, HtmlExtension)

  import info._
  val cssFile = new File(outputDirectory, CSSRelativePath)
  val jsFile = new File(outputDirectory, JSRelativePath)
  val jQueryFile = new File(outputDirectory, JQueryRelativePath)

  private val outputFiles = ArrayBuffer[FileWithLineCount]()

  def writeStart() {
    writeDefaultCSS(cssFile)
    writeJS(jsFile)
    writeJQuery(jQueryFile)
  }

  def writeUnit(sourceFile: File, relativeSourcePath: String, tokenList: List[Token]) {
    val outputFile = getOutputFile(relativeSourcePath)
    outputFiles += FileWithLineCount( outputFile , FileUtil.lineCount(sourceFile) )
    def relPath(f: File) = FileUtil.relativePath(outputFile, f)

    val styler = new BasicStyler(relativeSourcePath, relPath(cssFile), relPath(jsFile),  relPath(jQueryFile))
    Annotate(sourceFile, encoding, outputFile, tokenList, styler)
  }

  def writeEnd() {
    val indexFile = new File(outputDirectory, IndexRelativePath)
    writeIndex(indexFile, outputFiles.toList ,Nil)
  }
  
}
