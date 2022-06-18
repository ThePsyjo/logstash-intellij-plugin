package com.github.redfoos.logstash.completion

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.redfoos.logstash.LogstashLanguage
import com.github.redfoos.logstash.psi.LogstashTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.and
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext

class LogstashKeywordCompletionContributor : CompletionContributor() {
    companion object {
        private val LANG = psiElement().withLanguage(LogstashLanguage.INSTANCE)
        private val BAREWORD = psiElement(LogstashTypes.BAREWORD)
    }

    init {
        registerTopLevelCompletion()
        registerInputPluginCompletions()

    }

    private fun registerTopLevelCompletion() {
        val topLevelPattern = and(LANG, BAREWORD.withSuperParent(1, PsiErrorElement::class.java))
        val input = LookupElementBuilder.create("input")
            .withTypeText("An input plugin enables a specific source of events to be read")
            .withInsertHandler(LogstashInsertHandler.BRACE)
        val filter = LookupElementBuilder.create("filter")
            .withTypeText("An output plugin sends event data to a particular destination")
            .withInsertHandler(LogstashInsertHandler.BRACE)
        val output = LookupElementBuilder.create("output")
            .withTypeText("A filter plugin performs intermediary processing on an event")
            .withInsertHandler(LogstashInsertHandler.BRACE)
        extend(
            CompletionType.BASIC,
            topLevelPattern,
            LogstashCompletionProvider(listOf(input, filter, output))
        )
    }

    private fun registerInputPluginCompletions() {
        val inputPluginPattern = and(
            LANG, BAREWORD.withParent(
                psiElement(LogstashTypes.PLUGIN_SECTION).withFirstChild(
                    psiElement(LogstashTypes.INPUT_PLUGIN)
                )
            )
        )
        val stream = LogstashKeywordCompletionContributor::class.java.getResourceAsStream("/plugins/input_plugins.json")
        val objectMapper = jacksonObjectMapper()
        val config: List<Config> = stream.use {
            objectMapper.readValue(it)
        }

        val inputPlugins = config.map(::parsePlugin)
        registerPluginCompletions(inputPluginPattern, inputPlugins)
    }

    private fun registerPluginCompletions(
        topLevelPluginPattern: ElementPattern<PsiElement>,
        completions: List<Pair<LookupElementBuilder, Pair<ElementPattern<PsiElement>, List<LookupElementBuilder>>>>
    ) {
        val mainPluginCompletions = completions.map { it.first }
        extend(
            CompletionType.BASIC,
            topLevelPluginPattern,
            LogstashCompletionProvider(mainPluginCompletions)
        )
        completions.forEach {
            extend(
                CompletionType.BASIC,
                it.second.first,
                LogstashCompletionProvider(it.second.second)
            )
        }
    }


    private fun parsePlugin(pluginConfig: Config): Pair<LookupElementBuilder, Pair<ElementPattern<PsiElement>, List<LookupElementBuilder>>> {

        val insidePluginPattern = and(
            LANG,
            BAREWORD.withSuperParent(
                2,
                psiElement(LogstashTypes.PLUGIN).with(firstChileTextCondition(pluginConfig.pluginName))
            ),
        )

        val insidePluginCompletions = pluginConfig.pluginParameters.map {
            LookupElementBuilder.create(it.parameterName)
                .withTailText(it.parameterType)
                .withTypeText("Required: ${it.required}")
                .withInsertHandler(LogstashInsertHandler.ARROW)
        }
        val mainCompletion = LookupElementBuilder.create(pluginConfig.pluginName)
            .withTypeText(pluginConfig.pluginDescription)
            .withInsertHandler(LogstashInsertHandler.BRACE)

        return mainCompletion to (insidePluginPattern to insidePluginCompletions)
    }

    private fun firstChileTextCondition(pluginName: String): PatternCondition<PsiElement> {
        return object : PatternCondition<PsiElement>("look_for_${pluginName}_scope") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val child = t.firstChild
                if (child != null) {
                    return child is LeafPsiElement && child.text == pluginName
                }
                return false
            }
        }
    }
}

data class Config(
    val pluginName: String,
    val pluginDescription: String,
    val pluginParameters: List<PluginParameter>

)
data class PluginParameter(
    val parameterName: String,
    val parameterType: String,
    val required: String
)

class LogstashCompletionProvider(private val elements: List<LookupElement>) :
    CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        for (element in elements) {
            result.addElement(element)
        }
    }
}
