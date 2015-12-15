/*
 * Copyright (C) 2013 DroidDriver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.testing.espresso.BasicSample;

import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.core.deps.guava.base.Charsets;
import android.support.test.espresso.core.deps.guava.io.Files;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;
import android.util.Printer;
import android.util.StringBuilderPrinter;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Checkable;
import android.widget.TextView;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static org.hamcrest.Matchers.allOf;

/** Converts Espresso view tree to XML format that works with uiautomatorviewer **/
// https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L198
public class ViewToUix {
    private static final File dumpDir = new File(getTargetContext().getExternalCacheDir(), "dump");
    private static final File DUMP_XML = new File(dumpDir, "espresso_dump.uix");
    private static final File DUMP_PNG = new File(dumpDir, "espresso_image.png");
    private static final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static final String TAG = "espresso";

    // document needs to be static so that when buildDomNode is called recursively
    // on children they are in the same document to be appended.
    private static Document document;
    // The two maps should be kept in sync
    private static final Map<Object, Element> TO_DOM_MAP =
            new HashMap<>();

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L64
    private static void clearData() {
        TO_DOM_MAP.clear();
        document = null;
    }

    /**
     * Returns the DOM node representing this UiElement.
     */
    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L127
    private static Element getDomNode(View view, int index) {
        Element domNode = TO_DOM_MAP.get(view);
        if (domNode == null) {
            domNode = buildDomNode(view, index);
        }
        return domNode;
    }

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L113
    private static Document getDocument() {
        if (document == null) {
            try {
                document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
        return document;
    }

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/XPaths.java
    private static String tag(String className) {
        return simpleClassName(className);
    }

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/XPaths.java
    private static String simpleClassName(String name) {
        // the nth anonymous class has a class name ending in "Outer$n"
        // and local inner classes have names ending in "Outer.$1Inner"
        name = name.replaceAll("\\$[0-9]+", "\\$");

        // we want the name of the inner class all by its lonesome
        int start = name.lastIndexOf('$');

        // if this isn't an inner class, just find the start of the
        // top level class name.
        if (start == -1) {
            start = name.lastIndexOf('.');
        }
        return name.substring(start + 1);
    }


    // https://github.com/bootstraponline/uiautomator2/blob/fa866903ece601742d694279c03a6db48a953c22/src/main/java/android/support/test/uiautomator/AccessibilityNodeInfoDumper.java#L166
    private static String safeCharSeqToString(CharSequence cs) {
        if (cs == null)
            return "";
        else {
            return stripInvalidXMLChars(cs);
        }
    }

    // https://github.com/bootstraponline/uiautomator2/blob/fa866903ece601742d694279c03a6db48a953c22/src/main/java/android/support/test/uiautomator/AccessibilityNodeInfoDumper.java#L174
    private static String stripInvalidXMLChars(CharSequence cs) {
        StringBuffer ret = new StringBuffer();
        char ch;
        /* http://www.w3.org/TR/xml11/#charsets
        [#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
        [#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
        [#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
        [#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
        [#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
        [#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
        [#x10FFFE-#x10FFFF].
         */
        for (int i = 0; i < cs.length(); i++) {
            ch = cs.charAt(i);

            if ((ch >= 0x1 && ch <= 0x8) || (ch >= 0xB && ch <= 0xC) || (ch >= 0xE && ch <= 0x1F) ||
                    (ch >= 0x7F && ch <= 0x84) || (ch >= 0x86 && ch <= 0x9f) ||
                    (ch >= 0xFDD0 && ch <= 0xFDDF) || (ch >= 0x1FFFE && ch <= 0x1FFFF) ||
                    (ch >= 0x2FFFE && ch <= 0x2FFFF) || (ch >= 0x3FFFE && ch <= 0x3FFFF) ||
                    (ch >= 0x4FFFE && ch <= 0x4FFFF) || (ch >= 0x5FFFE && ch <= 0x5FFFF) ||
                    (ch >= 0x6FFFE && ch <= 0x6FFFF) || (ch >= 0x7FFFE && ch <= 0x7FFFF) ||
                    (ch >= 0x8FFFE && ch <= 0x8FFFF) || (ch >= 0x9FFFE && ch <= 0x9FFFF) ||
                    (ch >= 0xAFFFE && ch <= 0xAFFFF) || (ch >= 0xBFFFE && ch <= 0xBFFFF) ||
                    (ch >= 0xCFFFE && ch <= 0xCFFFF) || (ch >= 0xDFFFE && ch <= 0xDFFFF) ||
                    (ch >= 0xEFFFE && ch <= 0xEFFFF) || (ch >= 0xFFFFE && ch <= 0xFFFFF) ||
                    (ch >= 0x10FFFE && ch <= 0x10FFFF))
                ret.append(".");
            else
                ret.append(ch);
        }
        return ret.toString();
    }

    // https://raw.githubusercontent.com/bootstraponline/uiautomator2/android-support-test/src/main/java/android/support/test/uiautomator/AccessibilityNodeInfoHelper.java
    /**
     * Returns the node's bounds clipped to the size of the display
     *
     * @param node
     * @param width  pixel width of the display
     * @param height pixel height of the display
     * @return null if node is null, else a Rect containing visible bounds
     */
    static Rect getVisibleBoundsInScreen(AccessibilityNodeInfo node, int width, int height) {
        if (node == null) {
            return null;
        }
        // targeted node's bounds
        Rect nodeRect = new Rect();
        node.getBoundsInScreen(nodeRect);

        Rect displayRect = new Rect();
        displayRect.top = 0;
        displayRect.left = 0;
        displayRect.right = width;
        displayRect.bottom = height;

        // may change rect. must call
        //noinspection ResourceType
        nodeRect.intersect(displayRect);
        return nodeRect;
    }

    private static void attr(Element element, String attribute, CharSequence value) {
        element.setAttribute(attribute, safeCharSeqToString(value));
    }

    private static void attr(Element element, String attribute, Boolean value) {
        element.setAttribute(attribute, Boolean.toString(value));
    }

    private static void attr(Element element, String attribute, Integer value) {
        element.setAttribute(attribute, Integer.toString(value));
    }

    private static void attr(Element element, String attribute, Float value) {
        element.setAttribute(attribute, Float.toString(value));
    }

    // https://github.com/bootstraponline/platform_frameworks_testing/blob/fb9996b698387e62bd70fd8e42e4a5f0304d2d81/espresso/core/src/main/java/android/support/test/espresso/util/HumanReadables.java#L237
    private static void innerDescribe(Element element, TextView textBox) {
        if (null != textBox.getText()) {
            attr(element, "text", textBox.getText());
        }

        if (null != textBox.getError()) {
            attr(element, "error-text", textBox.getError());
        }

        if (null != textBox.getHint()) {
            attr(element, "hint", textBox.getHint());
        }

        attr(element, "input-type", textBox.getInputType());
        attr(element, "ime-target", textBox.isInputMethodTarget());
        attr(element, "has-links", textBox.getUrls().length > 0);
    }

    // https://github.com/bootstraponline/platform_frameworks_testing/blob/fb9996b698387e62bd70fd8e42e4a5f0304d2d81/espresso/core/src/main/java/android/support/test/espresso/util/HumanReadables.java#L255
    private static void innerDescribe(Element element, Checkable checkable) {
        attr(element, "is-checked", checkable.isChecked());
    }

    // https://github.com/bootstraponline/platform_frameworks_testing/blob/fb9996b698387e62bd70fd8e42e4a5f0304d2d81/espresso/core/src/main/java/android/support/test/espresso/util/HumanReadables.java#L259
    private static void innerDescribe(Element element, ViewGroup viewGroup) {
        attr(element, "child-count", viewGroup.getChildCount());
    }

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L136
    private static Element buildDomNode(View view, int index) {
        Element element = getDocument().createElement(tag("node"));
        TO_DOM_MAP.put(view, element);

        AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();

        if (Build.VERSION.SDK_INT >= 18) {
            // Manually set viewId since Espresso has no easy way of enabling
            // AccessibilityNodeInfo.FLAG_REPORT_VIEW_IDS
            // https://android.googlesource.com/platform/frameworks/base/+/47b50333c110194565498011379988e5c05f7890/core/java/android/view/View.java
            try {
                String viewId = view.getResources().getResourceName(view.getId());
                node.setViewIdResourceName(viewId);
            } catch (Resources.NotFoundException nfe) {
                    /* ignore */
            }
        }

        // https://github.com/bootstraponline/uiautomator2/blob/085063a4e394f8e9c6195ec00cdf81a2c2b4f1c9/src/main/java/android/support/test/uiautomator/AccessibilityNodeInfoDumper.java
        attr(element, "index", index);
        attr(element, "text", node.getText());
        if (Build.VERSION.SDK_INT >= 18) {
            attr(element, "resource-id", node.getViewIdResourceName()); // aka res-name
        }
        // node.getClassName() returns the accessibility class which isn't used by Espresso
        // espresso requires the concrete class. uiautomatorviewer looks nicer using simple names
        attr(element, "class", view.getClass().getSimpleName());
        attr(element, "package", node.getPackageName());
        attr(element, "content-desc", node.getContentDescription()); // aka desc
        attr(element, "checkable", node.isCheckable());
        attr(element, "checked", node.isChecked());
        attr(element, "clickable", node.isClickable()); // aka is-clickable
        attr(element, "enabled", node.isEnabled()); // aka is-enabled
        attr(element, "focusable", node.isFocusable()); // aka is-focusable
        attr(element, "focused", node.isFocused()); // aka is-focus
        attr(element, "scrollable", node.isScrollable());
        attr(element, "long-clickable", node.isLongClickable());
        attr(element, "password", node.isPassword());
        attr(element, "selected", node.isSelected()); // aka is-selected

        int width = device.getDisplayWidth();
        int height = device.getDisplayHeight();

        element.setAttribute("bounds", getVisibleBoundsInScreen(node, width, height).toShortString());

        // extra attributes
        attr(element, "full-class", view.getClass().getName()); // fully qualified class
        attr(element, "acc-class", node.getClassName()); // accessibility class

        // Espresso human readable attributes
        // https://github.com/bootstraponline/platform_frameworks_testing/blob/android-support-test/espresso/core/src/main/java/android/support/test/espresso/util/HumanReadables.java#L161
        attr(element, "view-id", view.getId()); // aka 'id'

        switch (view.getVisibility()) {
            case View.GONE:
                attr(element, "visibility", "GONE");
                break;
            case View.INVISIBLE:
                attr(element, "visibility", "INVISIBLE");
                break;
            case View.VISIBLE:
                attr(element, "visibility", "VISIBLE");
                break;
            default:
                attr(element, "visibility", view.getVisibility());
        }

        attr(element, "width", view.getWidth());
        attr(element, "height", view.getHeight());
        attr(element, "has-focus", view.hasFocus());
        attr(element, "has-focusable", view.hasFocusable());
        attr(element, "has-window-focus", view.hasWindowFocus());
        attr(element, "is-layout-requested", view.isLayoutRequested());

        if (null != view.getRootView()) {
            // pretty much only true in unit-tests.
            attr(element, "root-is-layout-requested", view.getRootView().isLayoutRequested());
        }

        EditorInfo ei = new EditorInfo();
        InputConnection ic = view.onCreateInputConnection(ei);
        boolean hasInputConnection = ic != null;
        attr(element, "has-input-connection", hasInputConnection);
        if (hasInputConnection) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            Printer p = new StringBuilderPrinter(sb);
            ei.dump(p, "");
            sb.append("]");
            attr(element, "editor-info", sb.toString().replace("\n", " "));
        }

        if (Build.VERSION.SDK_INT > 10) {
            attr(element, "view-x", view.getX());
            attr(element, "view-y", view.getY());
        }

        if (view instanceof TextView) {
            innerDescribe(element, (TextView) view);
        }

        if (view instanceof Checkable) {
            innerDescribe(element, (Checkable) view);
        }

        if (view instanceof ViewGroup) {
            innerDescribe(element, (ViewGroup) view);
        }

        // https://github.com/bootstraponline/espresso_2_clone/blob/436878d48678428fdbef57bdc90153833c1a3d61/espresso-core-2.0-sources/android/support/test/espresso/util/TreeIterables.java#L199
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                element.appendChild(getDomNode(group.getChildAt(i), i));
            }
        }

        return element;
    }

    // https://github.com/appium/droiddriver/blob/c1f89919ed5e88650548f34fe8ca9e5d458a41df/src/io/appium/droiddriver/finders/ByXPath.java#L198
    private static byte[] dumpDomBytes(View view) throws Exception {
        String path = DUMP_XML.toString();
        BufferedOutputStream bos = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            bos = new BufferedOutputStream(baos);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            clearData();

            Element domNode = getDomNode(view, 0);
            transformer.transform(new DOMSource(domNode), new StreamResult(bos));
            Log.i(TAG, "Wrote dom to " + path);
        } catch (Exception e) {
            Log.e(TAG, "Failed to transform node", e);
            throw e;
        } finally {
            clearData();
            if (bos != null) {
                try {
                    bos.flush();
                    bos.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * Dumps view as a String
     **/
    private static String dumpDom(View view) throws Exception {
        return new String(dumpDomBytes(view), Charsets.UTF_8);
    }

    /**
     * Dumps view xml and png.
     * Download with adb pull /sdcard/Android/data/com.example.android.testing.espresso.BasicSample/cache/dump
     **/
    private static boolean dumpToFile(View view) {
        boolean result = false;
        dumpDir.mkdirs();
        DUMP_PNG.delete();
        DUMP_XML.delete();
        try {
            device.takeScreenshot(DUMP_PNG);
            Files.write(dumpDomBytes(view), DUMP_XML);
            result = true;
        } catch (Exception e) {
            // ignored
        }
        return result;
    }

    /**
     * Invoke ViewToUix.dumpView(); from a test to dump the view
     **/
    public static void dumpView() {
        GetRootView getRoot = new GetRootView();
        onView(allOf(isRoot())).perform(getRoot);
        View rootView = getRoot.getRootView();

        boolean dumpResult = ViewToUix.dumpToFile(rootView);
        Assert.assertTrue(dumpResult);
    }
}
