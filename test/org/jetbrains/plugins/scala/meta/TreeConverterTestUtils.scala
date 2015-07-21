package org.jetbrains.plugins.scala.meta

import java.util.regex.{Matcher, Pattern}

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner
import org.jetbrains.plugins.scala.meta.trees.TreeConverter

import scala.meta.internal.ast.Tree

trait TreeConverterTestUtils {

  def fixture: CodeInsightTestFixture // we need to go deeper

  val converter: TreeConverter

  val startToken = "//start"

  def psiFromText(text: String): ScalaPsiElement = {
    def nextScalaPsiElement(current: PsiElement): ScalaPsiElement = current match {
      case _: PsiWhiteSpace => nextScalaPsiElement(current.getNextSibling)
      case sce: ScalaPsiElement => sce
      case _: PsiElement => nextScalaPsiElement(current.getNextSibling)
    }
    val file: ScalaFile = parseTextToFile(text)
    val startPos = file.getText.indexOf(startToken)
    if (startPos < 0)
      file.typeDefinitions.headOption.getOrElse(file.getImportStatements.head)
    else {
      val element = file.findElementAt(startPos)
      element.getParent match {
        case parent: ScCommentOwner => parent.asInstanceOf[ScalaPsiElement]
        case _ => nextScalaPsiElement(element)
      }
    }
  }

  def structuralEquals(tree1: Tree, tree2: Tree): Boolean = {
    // NOTE: for an exhaustive list of tree field types see
    // see /foundation/src/main/scala/org/scalameta/ast/internal.scala
    def loop(x1: Any, x2: Any): Boolean = (x1, x2) match {
      case (x1: Tree, x2: Tree) => structuralEquals(x1, x2)
      case (Some(x1), Some(x2)) => loop(x1, x2)
      case (Seq(xs1@_*), Seq(xs2@_*)) => xs1.zip(xs2).forall { case (x1, x2) => loop(x1, x2)}
      case (x1, x2) => x1 == x2
    }
    def tagsEqual = tree1.internalTag == tree2.internalTag
    def fieldsEqual = tree1.productIterator.toList.zip(tree2.productIterator.toList).forall { case (x1, x2) => loop(x1, x2)}
    (tagsEqual && fieldsEqual) || {println(s"${tree1.show[scala.meta.Raw]} <=> ${tree2.show[scala.meta.Raw]}"); false}
  }

  def doTest(text: String, tree: Tree) = {
    val converted = convert(text)
    assert(structuralEquals(converted, tree), s"$converted <=> $tree")
    assert(converted.toString() == tree.toString(), s"TEXT: $converted <=> $tree")
  }

  protected def convert(text: String): Tree = {
    val psi = psiFromText(text)
    converter.ideaToMeta(psi)
  }

  def parseTextToFile(@Language("Scala") str: String): ScalaFile = {
    def isTopLevel = Pattern.compile("^\\s*(class|trait|object|import|package).*", Pattern.DOTALL).matcher(str).matches()
    val text = if (!isTopLevel)
      s"""
        |object Dummy {
        |${if (!str.contains(startToken)) startToken else ""}
        |$str
        |}
      """.stripMargin
    else str
    fixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
  }
}
