/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.originalDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTowerDataContext
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirTowerDataContextCollector
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addIfNotNull

interface FirTowerContextProvider {
    fun getClosestAvailableParentContext(ktElement: KtElement): FirTowerDataContext?
}

internal class FileTowerProvider(
    private val file: KtFile,
    private val context: FirTowerDataContext
) : FirTowerContextProvider {
    override fun getClosestAvailableParentContext(ktElement: KtElement): FirTowerDataContext? =
        if (file == ktElement.containingKtFile) context else null
}

internal class FirTowerDataContextAllElementsCollector : FirTowerDataContextCollector, FirTowerContextProvider {
    private val elementsToContext: MutableMap<KtElement, FirTowerDataContext> = hashMapOf()

    /**
     * Class headers have context which is different from the context inside of the class, so we have
     * to collect the scopes for it separately.
     *
     * See [containsInHeader] for more info.
     */
    private val classesToClassHeaderContext: MutableMap<KtClassOrObject, FirTowerDataContext> = hashMapOf()

    override fun addFileContext(file: FirFile, context: FirTowerDataContext) {
        val ktFile = file.psi as? KtFile ?: return
        elementsToContext[ktFile] = context
    }

    override fun addStatementContext(statement: FirStatement, context: FirTowerDataContext) {
        val closestStatementInBlock = statement.psi?.closestBlockLevelOrInitializerExpression() ?: return
        // FIR body transform may alter the context if there are implicit receivers with smartcast
        elementsToContext[closestStatementInBlock] = context.createSnapshot()
    }

    override fun addDeclarationContext(declaration: FirDeclaration, context: FirTowerDataContext) {
        val psi = declaration.psi as? KtElement ?: return

        // FIR creates a fake field declaration for the delegated super calls,
        // and it makes handling class headers more difficult, so
        // we do not collect contexts for such declarations
        if (psi is KtDelegatedSuperTypeEntry) return

        elementsToContext[psi] = context
    }

    override fun addClassHeaderContext(declaration: FirRegularClass, context: FirTowerDataContext) {
        val psi = declaration.psi as? KtElement ?: return
        if (psi !is KtClassOrObject) return
        classesToClassHeaderContext[psi] = context
    }

    override fun getClosestAvailableParentContext(ktElement: KtElement): FirTowerDataContext? {
        var current: PsiElement? = ktElement
        while (current != null) {
            if (current is KtElement) {
                getContextForElementOnPosition(current, ktElement)?.let { return it }
            }
            if (current is KtFile) {
                break
            }
            current = current.parent
        }
        return null
    }

    /**
     * N.B. It is expected that [element] contains [position].
     */
    private fun getContextForElementOnPosition(element: KtElement, position: KtElement): FirTowerDataContext? {
        val contextMapping =
            if (element is KtClassOrObject && element.containsInHeader(position)) {
                classesToClassHeaderContext
            } else {
                elementsToContext
            }

        return getContextForElement(element, contextMapping)
    }

    private fun getContextForElement(element: KtElement, contextMapping: Map<out KtElement, FirTowerDataContext>): FirTowerDataContext? {
        contextMapping[element]?.let { return it }

        if (element is KtDeclaration) {
            val originalDeclaration = element.originalDeclaration
            originalDeclaration?.let { contextMapping[it] }?.let { return it }
        }

        return null
    }
}

private tailrec fun PsiElement.closestBlockLevelOrInitializerExpression(): KtExpression? =
    when {
        this is KtExpression && (parent is KtBlockExpression || parent is KtDeclarationWithInitializer) -> this
        else -> parent?.closestBlockLevelOrInitializerExpression()
    }

/**
 * Returns true if [element] is considered to be a part of [this] class header.
 *
 * Here by "header" we understand the parts of the class declaration which resolution is not affected
 * by the class own supertypes.
 */
private fun KtClassOrObject.containsInHeader(element: PsiElement): Boolean {
    if (this == element) return false

    val classHeaderParts = buildSet<KtElement> {
        addIfNotNull(getContextReceiverList())
        addIfNotNull(modifierList) // for annotations declared on class
        addIfNotNull(typeParameterList)

        for (superTypeEntry in superTypeListEntries) {
            addIfNotNull(superTypeEntry.typeReference)
        }
    }

    if (classHeaderParts.isEmpty()) return false

    val elementParentsUntilClass = element.parentsWithSelf.takeWhile { it != this }
    return elementParentsUntilClass.any { it in classHeaderParts }
}
