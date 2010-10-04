package org.jetbrains.plugins.scala
package lang
package formatting
/**
* @author ilyas
*/

import settings.ScalaCodeStyleSettings

import java.util.ArrayList;

import com.intellij.formatting._;
import com.intellij.psi.tree._;
import com.intellij.lang.ASTNode;






import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.lang.formatting.processors._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import ScalaWrapManager._



object getDummyBlocks {

  def apply(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      case _: ScIfStmt => {
        val alignment = if (settings.ALIGN_IF_ELSE) Alignment.createAlignment
                        else null
        subBlocks.addAll(getIfSubBlocks(node, block, alignment))
        return subBlocks
      }
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixTypeElement => {
        subBlocks.addAll(getInfixBlocks(node, block))
        return subBlocks
      }
      case _: ScExtendsBlock => {
        subBlocks.addAll(getExtendsSubBlocks(node, block))
        return subBlocks
      }
      case _ =>
    }
    val alignment = if (mustAlignment(node, block.getSettings))
      Alignment.createAlignment
    else null
    for (val child <- children if isCorrectBlock(child)) {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      val childAlignment = {
        node.getPsi match {
          case _: ScParameterClause => {
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => null
              case _ => alignment
            }
          }
          case _: ScXmlStartTag  | _: ScXmlEmptyTag => {
            child.getElementType match {
              case ScalaElementTypes.XML_ATTRIBUTE => alignment
              case _ => null
            }
          }
          case _: ScXmlElement => {
            child.getElementType match {
              case ScalaElementTypes.XML_START_TAG | ScalaElementTypes.XML_END_TAG => alignment
              case _ => null
            }
          }
          case _ => alignment
        }
      }
      val childWrap = arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      prevChild = child
    }
    return subBlocks
  }

  def apply(node: ASTNode, lastNode: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    var child = node
    while (child != lastNode) {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      if (isCorrectBlock(child)) {
        val childWrap = arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, child, null, null, indent, childWrap, block.getSettings))
      }
      child = child.getTreeNext
    }
    val indent = ScalaIndentProcessor.getChildIndent(block, lastNode)
    if (isCorrectBlock(lastNode)) {
      val childWrap = arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, lastNode, null, null, indent, childWrap, block.getSettings))
    }
    return subBlocks
  }

  private def getExtendsSubBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val extBlock: ScExtendsBlock = node.getPsi.asInstanceOf[ScExtendsBlock]
    if (extBlock.getFirstChild == null) return subBlocks
    val tempBody = extBlock.templateBody
    val first = extBlock.getFirstChild
    val last = tempBody match {
      case None => extBlock.getLastChild
      case Some(x) => x.getPrevSibling
    }
    if (last != null) {
      val indent = ScalaIndentProcessor.getChildIndent(block, first.getNode)
      val childWrap = arrangeSuggestedWrapForChild(block, first.getNode, settings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, first.getNode, last.getNode, null, indent, childWrap, block.getSettings))
    }

    tempBody match {
      case Some(x) => {
        val indent = ScalaIndentProcessor.getChildIndent(block, x.getNode)
        val childWrap = arrangeSuggestedWrapForChild(block, x.getNode, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, x.getNode, null, null, indent, childWrap, block.getSettings))
      }
      case _ =>
    }
    return subBlocks
  }

  private def getIfSubBlocks(node: ASTNode, block: ScalaBlock, alignment: Alignment): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val firstChildNode = node.getFirstChildNode
    var child = firstChildNode
    while (child.getTreeNext != null && child.getTreeNext.getElementType != ScalaTokenTypes.kELSE) {
      child = child.getTreeNext
    }
    val indent = ScalaIndentProcessor.getChildIndent(block, firstChildNode)
    val childWrap = arrangeSuggestedWrapForChild(block, firstChildNode, settings, block.suggestedWrap)
    val firstBlock = new ScalaBlock(block, firstChildNode, child, alignment, indent, childWrap, block.getSettings)
    subBlocks.add(firstBlock)
    if (child.getTreeNext != null) {
      val firstChild = child.getTreeNext
      child = firstChild
      val back: ASTNode = null
      while (child.getTreeNext != null) {
        child.getTreeNext.getPsi match {
          case _: ScIfStmt => {
            val childWrap = arrangeSuggestedWrapForChild(block, firstChild, settings, block.suggestedWrap)
            subBlocks.add(new ScalaBlock(block, firstChild, child, alignment, indent, childWrap, block.getSettings))
            subBlocks.addAll(getIfSubBlocks(child.getTreeNext, block, alignment))
          }
          case _ =>
        }
        child = child.getTreeNext
      }
      if (subBlocks.size ==  1) {
        val childWrap = arrangeSuggestedWrapForChild(block, firstChild, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, firstChild, child, alignment, indent, childWrap, block.getSettings))
      }
    }
    return subBlocks
  }

  private def getInfixBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val children = node.getChildren(null)
    for (val child <- children) {
      def checkSamePriority: Boolean = {
        import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
        val childPriority = child.getPsi match {
          case inf: ScInfixExpr => priority(inf.operation.getText, true)
          case inf: ScInfixPattern => priority(inf.refernece.getText, false)
          case inf: ScInfixTypeElement => priority(inf.ref.getText, false)
          case _ => 0
        }
        val parentPriority = node.getPsi match {
          case inf: ScInfixExpr => priority(inf.operation.getText, true)
          case inf: ScInfixPattern => priority(inf.refernece.getText, false)
          case inf: ScInfixTypeElement => priority(inf.ref.getText, false)
          case _ => 0
        }
        parentPriority == childPriority
      }
      if (INFIX_ELEMENTS.contains(child.getElementType) && checkSamePriority) {
        subBlocks.addAll(getInfixBlocks(child, block))
      } else if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        val alignment = if (mustAlignment(node, block.getSettings))
          Alignment.createAlignment
        else null
        val childWrap = arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, child, null, alignment, indent, childWrap, block.getSettings))
      }
    }
    subBlocks
  }

  private def isCorrectBlock(node: ASTNode) = {
    node.getText().trim().length() > 0
  }

  private def mustAlignment(node: ASTNode, mySettings: CodeStyleSettings) = {
    val scalaSettings = mySettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      //case _: ScXmlExpr => true //todo: setting
      case _: ScXmlStartTag => true //todo: setting
      case _: ScXmlEmptyTag => true //todo: setting for attributes
      //case _: ScXmlElement => true //todo: setting
      case _: ScParameters if scalaSettings.ALIGN_MULTILINE_PARAMETERS => true
      case _: ScParameterClause if scalaSettings.ALIGN_MULTILINE_PARAMETERS => true
      case _: ScTemplateParents if scalaSettings.ALIGN_MULTILINE_EXTENDS_LIST => true
      case _: ScArguments if scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => true
      case _: ScPatternArgumentList if scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => true
      case _: ScEnumerators if scalaSettings.ALIGN_MULTILINE_FOR => true
      case _: ScParenthesisedExpr if scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedTypeElement if scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedPattern if scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScInfixExpr if scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixPattern if scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixTypeElement if scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScIdList if scalaSettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION => true
      case _: ScIfStmt => true
      case _ => false
    }
  }

  private val INFIX_ELEMENTS = TokenSet.create(ScalaElementTypes.INFIX_EXPR,
  ScalaElementTypes.INFIX_PATTERN,
  ScalaElementTypes.INFIX_TYPE)

}