package com.writely.syntax_analyzer.app.guide;

/**
 * Sealed interface representing an immutable content block in an onboarding guide section.
 */
public sealed interface GuideBlock permits ParagraphBlock, BulletListBlock, SnippetBlock {
}
