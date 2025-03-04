package org.asciidoc.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.TokenSet;
import org.asciidoc.intellij.AsciiDoc;
import org.asciidoc.intellij.lexer.AsciiDocTokenTypes;
import org.asciidoc.intellij.parser.AsciiDocElementTypes;
import org.asciidoc.intellij.parser.AsciiDocParserImpl;
import org.jetbrains.annotations.NotNull;

public class AsciiDocHeading extends ASTWrapperPsiElement {
  private static final com.intellij.openapi.diagnostic.Logger LOG =
    com.intellij.openapi.diagnostic.Logger.getInstance(AsciiDoc.class);

  private static final TokenSet HEADINGS = TokenSet.create(AsciiDocTokenTypes.HEADING_TOKEN, AsciiDocTokenTypes.HEADING_OLDSTYLE);

  public AsciiDocHeading(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  public static TextRange getBodyRange(AsciiDocHeading element) {
    return element.getTextRange();
  }

  private static String trimHeading(String text) {
    if (text.charAt(0) == '=') {
      // new style heading
      text = StringUtil.trimLeading(text, '=').trim();
    } else if (text.charAt(0) == '#') {
      // markdown style heading
      text = StringUtil.trimLeading(text, '#').trim();
    } else {
      // old style heading
      text = text.replaceAll("[-=~^+\n \t]*$", "");
    }
    return text;
  }

  public String getHeadingText(boolean substitution) {
    StringBuilder sb = new StringBuilder();
    PsiElement child = getFirstChild();
    boolean hasAttribute = false;
    while (child != null) {
      if (HEADINGS.contains(child.getNode().getElementType())) {
        sb.append(child.getText());
      } else if (child instanceof AsciiDocAttributeReference) {
        hasAttribute = true;
        sb.append(child.getText());
      }
      child = child.getNextSibling();
    }
    if (hasAttribute && substitution) {
      try {
        String resolved = AsciiDocUtil.resolveAttributes(this, sb.toString());
        if (resolved != null && resolved.length() > 0) {
          sb.replace(0, sb.length(), resolved);
        }
      } catch (IndexNotReadyException ex) {
        // noop
      }
    }
    if (sb.length() == 0) {
      LOG.error("unable to extract heading text", AsciiDocPsiImplUtil.getRuntimeException("unable to extract heading text", this.getParent(), null));
      return "???";
    }
    return trimHeading(sb.toString());
  }

  public int getHeadingLevel() {
    return AsciiDocParserImpl.headingLevel(getText());
  }

  public AsciiDocBlockId getBlockId() {
    AsciiDocBlockId blockId = findLastChildByType(AsciiDocElementTypes.BLOCKID);

    // only if the block ID is the last element in the heading it is the block ID of the section
    if (blockId != null &&
      blockId.getNextSibling() != null &&
      blockId.getNextSibling().getNode().getElementType() == AsciiDocTokenTypes.INLINEIDEND &&
      blockId.getNextSibling().getNextSibling() == null) {
      return blockId;
    }

    return null;
  }

}
