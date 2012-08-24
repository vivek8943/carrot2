package org.carrot2.text.preprocessing.pipeline;

import java.io.Reader;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKWidthFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.ja.JapaneseBaseFormFilter;
import org.apache.lucene.analysis.ja.JapaneseKatakanaStemFilter;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer.Mode;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.util.Version;
import org.carrot2.core.Document;
import org.carrot2.core.LanguageCode;
import org.carrot2.core.attribute.Init;
import org.carrot2.core.attribute.Internal;
import org.carrot2.core.attribute.Processing;
import org.carrot2.text.analysis.ITokenizer;
import org.carrot2.text.linguistic.DefaultLexicalDataFactory;
import org.carrot2.text.linguistic.ILexicalDataFactory;
import org.carrot2.text.linguistic.IStemmer;
import org.carrot2.text.linguistic.IStemmerFactory;
import org.carrot2.text.linguistic.ITokenizerFactory;
import org.carrot2.text.linguistic.LanguageModel;
import org.carrot2.text.preprocessing.CaseNormalizer;
import org.carrot2.text.preprocessing.DocumentAssigner;
import org.carrot2.text.preprocessing.LabelFilterProcessor;
import org.carrot2.text.preprocessing.PhraseExtractor;
import org.carrot2.text.preprocessing.PreprocessingContext;
import org.carrot2.util.attribute.Attribute;
import org.carrot2.util.attribute.AttributeLevel;
import org.carrot2.util.attribute.DefaultGroups;
import org.carrot2.util.attribute.Group;
import org.carrot2.util.attribute.Input;
import org.carrot2.util.attribute.Level;
import org.carrot2.util.attribute.constraint.ImplementingClasses;

/**
 * A {@link IPreprocessingPipeline} based on tokenizers and filtering 
 * components from the Apache Lucene project.
 * 
 * @see "http://lucene.apache.org"
 */
public class LucenePreprocessingPipeline implements IPreprocessingPipeline
{
    /** A dummy unusable {@link IStemmerFactory}. */
    private final static IStemmerFactory NOSTEMMER = new IStemmerFactory() {
        @Override
        public IStemmer getStemmer(LanguageCode languageCode) { throw new RuntimeException("Not available."); }
    };

    /** A dummy unusable {@link ITokenizerFactory}. */
    private final static ITokenizerFactory NOTOKENIZER = new ITokenizerFactory() {
        @Override
        public ITokenizer getTokenizer(LanguageCode languageCode) { throw new RuntimeException("Not available."); }
    };

    /**
     * Lexical data factory. Creates the lexical data to be used by the clustering
     * algorithm, including stop word and stop label dictionaries.
     */
    @Input
    @Init
    @Processing
    @Internal
    @Attribute
    @ImplementingClasses(classes = {}, strict = false)
    @Level(AttributeLevel.ADVANCED)
    @Group(DefaultGroups.PREPROCESSING)
    public ILexicalDataFactory lexicalDataFactory = new DefaultLexicalDataFactory();

    /** */
    private final LuceneAnalyzerPreprocessor preprocessor = new LuceneAnalyzerPreprocessor();
    
    /**
     * Case normalizer used by the algorithm, contains bindable attributes.
     */
    public final CaseNormalizer caseNormalizer = new CaseNormalizer();

    /**
     * Phrase extractor used by the algorithm, contains bindable attributes.
     */
    public final PhraseExtractor phraseExtractor = new PhraseExtractor();

    /**
     * Label filter processor used by the algorithm, contains bindable attributes.
     */
    public final LabelFilterProcessor labelFilterProcessor = new LabelFilterProcessor();

    /**
     * Document assigner used by the algorithm, contains bindable attributes.
     */
    public final DocumentAssigner documentAssigner = new DocumentAssigner();

    @Override
    public PreprocessingContext preprocess(List<Document> documents, String query, LanguageCode language)
    {
        // Pick an analyzer based on the language.
        final Analyzer analyzer = pickAnalyzer(language);

        // Process the input.
        final PreprocessingContext context = new PreprocessingContext(
            LanguageModel.create(language, 
                NOSTEMMER, 
                NOTOKENIZER, 
                lexicalDataFactory), documents, query);

        // Finalize processing steps.
        preprocessor.preprocessDocuments(context, analyzer);
        caseNormalizer.normalize(context);
        preprocessor.fillStemData(context);
        phraseExtractor.extractPhrases(context);
        labelFilterProcessor.process(context);
        documentAssigner.assign(context);

        context.preprocessingFinished();
        return context;
    }

    /**
     * Pick analyzer and token stream for the given language.
     */
    protected Analyzer pickAnalyzer(LanguageCode language)
    {
        // TODO: maybe we could make it a set of fields (englishAnalyzer, frenchAnalyzer), provide
        // the defaults but then users could replace a single analyzer without the need of subclassing
        // or rewriting a preprocessing pipeline for all languages?
        switch (language) {
            case ENGLISH:
                return defaultEnglishAnalyzer();
            case JAPANESE:
                return defaultJapaneseAnalyzer();
            default:
                throw new RuntimeException(this.getClass().getSimpleName() + 
                    " cannot be used with language: " + language);
        }
    }

    private Analyzer defaultEnglishAnalyzer()
    {
        return new Analyzer()
        {
            @SuppressWarnings("deprecation")
            @Override
            public TokenStream tokenStream(String field, Reader reader)
            {
                Version matchVersion = Version.LUCENE_CURRENT;
                TokenStream result = new LuceneExtendedWhitespaceTokenizer(reader);
                result = new EnglishPossessiveFilter(matchVersion, result);
                result = new LowerCaseFilter(matchVersion, result);
                result = new CommonWordMarkerFilter(result, lexicalDataFactory.getLexicalData(LanguageCode.ENGLISH));
                result = new PorterStemFilter(result);
                return result;
            }
        };
    }

    private Analyzer defaultJapaneseAnalyzer()
    {
        return new Analyzer()
        {
            @Override
            public TokenStream tokenStream(String field, Reader reader)
            {
                TokenStream result = new JapaneseTokenizer(
                    reader, null, false, JapaneseTokenizer.DEFAULT_MODE);
                result = new JapaneseBaseFormFilter(result);
                result = new CJKWidthFilter(result);
                result = new JapaneseKatakanaStemFilter(result);
                result = new JapaneseTokenTypeConverter(result);
                return result;
            }
        };
    }    
}