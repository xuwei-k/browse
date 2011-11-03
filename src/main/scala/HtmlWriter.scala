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

  @inline final val header = Seq(
     "<html>","<head>"
    ,"""<meta http-equiv="Pragma" content="no-cache">"""
    ,"""<meta http-equiv="Cache-Control" content="no-store">"""
    ,"""<meta http-equiv="Cache-Control" content="no-cache">"""
    ,"""<meta http-equiv="Expires" content="-1">"""
    ,"</head>"
    ,"<body>"
    ,"<ol>").mkString("\n")

  def writeIndex(to: File, files: List[FileWithLineCount])
  {
    try{
    val relativizeAgainst = to.getParentFile
    val pathAndCounts = for{
      f     <- files
      path  <- FileUtil.relativize(relativizeAgainst, f.file).toList
    }yield PathAndLineCount(path,f.lineCount)

    FileUtil.withWriter(to) { out =>
      out.write(header)
      pathAndCounts.sortBy(_.path).foreach(writeEntry(out))
      out.write("</ol>\n</body>\n</html>")
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
    writeIndex(indexFile, outputFiles.toList )
  }
  
}
