/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.versioning.xquery;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.dom.persistent.QName;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.versioning.VersioningTrigger;
import org.exist.versioning.Diff;
import org.exist.versioning.StandardDiff;
import org.exist.versioning.DiffException;
import org.xml.sax.SAXException;

import java.util.Properties;
import java.util.Date;

public class DiffFunction extends BasicFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName( "diff", VersioningModule.NAMESPACE_URI, VersioningModule.PREFIX ),
                    "Returns a diff between two documents (which normally means two " +
                    "versions of the same document). Both documents should be stored in the " +
                    "database. The function will not work with in-memory documents. The returned " +
                    "diff uses the same format as generated by the VersioningTrigger.",
                    new SequenceType[] {
                            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType( Type.NODE, Cardinality.EXACTLY_ONE )
            );

    public DiffFunction(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        NodeValue nv1 = (NodeValue) args[0].itemAt(0);
        NodeValue nv2 = (NodeValue) args[1].itemAt(0);
        if (nv1.getImplementationType() != NodeValue.PERSISTENT_NODE ||
                nv2.getImplementationType() != NodeValue.PERSISTENT_NODE)
            throw new XPathException(this, "diff function only works on persistent documents stored in the db");
        DocumentImpl doc1 = ((NodeProxy)nv1).getOwnerDocument();
        DocumentImpl doc2 = ((NodeProxy)nv2).getOwnerDocument();

        context.pushDocumentContext();
        try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            Properties properties = new Properties();
            properties.setProperty("document", doc1.getURI().toString());
            properties.setProperty("revision", "");
            properties.setProperty("date", new DateTimeValue(new Date()).getStringValue());
            properties.setProperty("user", context.getUser().getName());

            int nodeNr = builder.startElement(VersioningTrigger.ELEMENT_VERSION, null);
            VersioningTrigger.writeProperties(receiver, properties);

            Diff diff = new StandardDiff(context.getBroker());
            diff.diff(doc1, doc2);
            diff.diff2XML(receiver);

            builder.endElement();
            return builder.getDocument().getNode(nodeNr);
        } catch (SAXException e) {
            throw new XPathException(this, "Caugt error while generating diff: " + e.getMessage(), e);
        } catch (DiffException e) {
            throw new XPathException(this, "Caugt error while generating diff: " + e.getMessage(), e);
        } finally {
            context.popDocumentContext();
        }
    }
}
