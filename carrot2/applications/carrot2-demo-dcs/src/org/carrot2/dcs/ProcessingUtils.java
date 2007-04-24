
/*
 * Carrot2 project.
 *
 * Copyright (C) 2002-2007, Dawid Weiss, Stanisław Osiński.
 * Portions (C) Contributors listed in "carrot2.CONTRIBUTORS" file.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the repository checkout or at:
 * http://www.carrot2.org/carrot2.LICENSE
 */

package org.carrot2.dcs;

import java.io.*;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.carrot2.core.LocalController;
import org.carrot2.core.LocalInputComponent;
import org.carrot2.core.impl.*;
import org.carrot2.util.PerformanceLogger;
import org.carrot2.util.StringUtils;

/**
 * Utilities for processing requests and C2 data streams.
 */
public class ProcessingUtils
{
    private ProcessingUtils()
    {
        // no instances.
    }

    /**
     * Run clustering for input files.
     */
    public static void cluster(String processName, LocalController controller, Logger logger, InputStream inputXML,
        OutputStream outputXML, boolean clustersOnly) throws Exception
    {
        final PerformanceLogger plogger = new PerformanceLogger(Level.DEBUG, logger);
        ArrayOutputComponent.Result result;
        try
        {
            plogger.start("Processing request (algorithm: " + processName + ")");

            // Phase 1 -- read the XML
            plogger.start("Reading XML");

            final HashMap requestProperties = new HashMap();
            requestProperties.put(XmlStreamInputComponent.XML_STREAM, inputXML);
            result = (ArrayOutputComponent.Result) controller.query(ControllerContext.STREAM_TO_RAWDOCS, "n/a",
                requestProperties).getQueryResult();

            final List documents = result.documents;
            final String query = (String) requestProperties.get(LocalInputComponent.PARAM_QUERY);

            plogger.end("Documents: " + documents.size() + ", query: " + query);

            // Phase 2 -- cluster documents
            plogger.start("Clustering");

            requestProperties.clear();
            requestProperties.put(ArrayInputComponent.PARAM_SOURCE_RAW_DOCUMENTS, documents);
            requestProperties.put(LocalInputComponent.PARAM_REQUESTED_RESULTS, Integer.toString(documents.size()));

            result = (ArrayOutputComponent.Result) controller.query(processName, query, requestProperties)
                .getQueryResult();
            final List clusters = result.clusters;

            plogger.end();

            // Phase 3 -- save the result or emit it somehow.
            plogger.start("Saving result");

            requestProperties.clear();
            requestProperties.put(ArrayInputComponent.PARAM_SOURCE_RAW_DOCUMENTS, documents);
            requestProperties.put(ArrayInputComponent.PARAM_SOURCE_RAW_CLUSTERS, clusters);
            requestProperties.put(SaveFilterComponentBase.PARAM_OUTPUT_STREAM, outputXML);
            requestProperties.put(SaveFilterComponentBase.PARAM_SAVE_CLUSTERS, Boolean.TRUE);
            requestProperties.put(SaveFilterComponentBase.PARAM_SAVE_DOCUMENTS, new Boolean(!clustersOnly));

            controller.query(ControllerContext.RESULTS_TO_XML, query, requestProperties);

            plogger.end();
        }
        catch (IOException e)
        {
            logger.warn("Processing failed: " + StringUtils.chainExceptionMessages(e));
            logger.debug("Processing failed (full stack): ", e);
        }
        finally
        {
            plogger.reset();
        }
    }
}
