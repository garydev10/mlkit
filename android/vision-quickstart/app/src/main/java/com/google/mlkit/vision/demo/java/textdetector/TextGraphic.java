/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java.textdetector;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.Text.Element;
import com.google.mlkit.vision.text.Text.Line;
import com.google.mlkit.vision.text.Text.Symbol;
import com.google.mlkit.vision.text.Text.TextBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * overlay view.
 */
public class TextGraphic extends Graphic {

  private static final String TAG = "TextGraphic";
  private static final String TEXT_WITH_LANGUAGE_TAG_FORMAT = "%s:%s";

  private static final int TEXT_COLOR = Color.BLACK;
  private static final int MARKER_COLOR = Color.YELLOW;
  private static final int MARKER_COLOR2 = Color.GREEN;
  private static final float TEXT_SIZE = 54.0f;
  private static final float STROKE_WIDTH = 4.0f;

  private final Paint rectPaint;
  private final Paint rectPaint2;
  private final Paint textPaint;
  private final Paint labelPaint;
  private final Paint labelPaint2;
  private final Text text;
  private final boolean shouldGroupTextInBlocks;
  private final boolean showLanguageTag;
  private final boolean showConfidence;

  TextGraphic(
      GraphicOverlay overlay,
      Text text,
      boolean shouldGroupTextInBlocks,
      boolean showLanguageTag,
      boolean showConfidence) {
    super(overlay);

    this.text = text;
    this.shouldGroupTextInBlocks = shouldGroupTextInBlocks;
    this.showLanguageTag = showLanguageTag;
    this.showConfidence = showConfidence;

    rectPaint = new Paint();
    rectPaint.setColor(MARKER_COLOR);
    rectPaint.setStyle(Paint.Style.STROKE);
    rectPaint.setStrokeWidth(STROKE_WIDTH);
    
    rectPaint2 = new Paint();
    rectPaint2.setColor(MARKER_COLOR2);
    rectPaint2.setStyle(Paint.Style.STROKE);
    rectPaint2.setStrokeWidth(STROKE_WIDTH);

    textPaint = new Paint();
    textPaint.setColor(TEXT_COLOR);
    textPaint.setTextSize(TEXT_SIZE);

    labelPaint = new Paint();
    labelPaint.setColor(MARKER_COLOR);
    labelPaint.setStyle(Paint.Style.FILL);

    labelPaint2 = new Paint();
    labelPaint2.setColor(MARKER_COLOR2);
    labelPaint2.setStyle(Paint.Style.FILL);

    // Redraw the overlay, as this graphic has been added.
    postInvalidate();
  }

  private static boolean isLabelMatch(List<String> texts, String text) {
    boolean result = false;
    if (text.startsWith("TOOL")) {
      Pattern p = Pattern.compile("\\d+");
      Matcher m = p.matcher(text);
      if (m.find()) {
        String batch = m.group() + "/";
        for (String t : texts) {
          if (t.endsWith((batch))) {
            result = true;
            break;
          }
        }
      }
    } else if (text.endsWith("/")) {
      Pattern p = Pattern.compile("\\d+/");
      Matcher m = p.matcher(text);
      if (Collections.frequency(texts, text) >= 2) {
        result = true;
      } else if (m.find()) {
        String batch = m.group();
        for (String t : texts) {
          Pattern p2 = Pattern.compile("TOOL*\\d+/");
          Matcher m2 = p2.matcher(t);
          if (m2.find()) {
            String batch2 = m2.group();
            if (batch2.equals(batch)) {
              result = true;
            }
          }
        }
      }
    } else if (Collections.frequency(texts, text.replace('O','0')) >= 2) {
      result = true;
    } else if (text.length() >= 7) {
      int offset = text.toUpperCase().startsWith("W/")? 1: 0;
      int i = text.toUpperCase().indexOf("W", offset);
      int count = 0;
      if (i >= 0) {
        String workOrderNum = text.substring(i + 1);
        for (String t : texts) {
          if (t.endsWith(workOrderNum)) {
            count ++;
          }
        }
        if (count >= 2) {
          result = true;
        }
      }
    }

    Log.d(TAG, String.format("isLabelMatch text is: %s result is %b", text, result));
    return result;
  }

  private static boolean isLabelInScope(String text) {
    boolean result = false;
    if (text.startsWith("TOOL")) {
      result = true;
    } else if (text.endsWith("/")) {
      result = true;
    } else if (text.contains(".") && text.length() > 5 && text.toUpperCase().equals(text)) {
      result = true;
    } else if (text.length() >= 7) {
      int offset = text.toUpperCase().startsWith("W/")? 1: 0;
      int i = text.toUpperCase().indexOf("W", offset);
      if (i>=0 && TextUtils.isDigitsOnly(text.substring(i + 1))) {
        result = true;
      }
    }
    Log.d(TAG, String.format("isLabelInScope text is: %s result is %b", text, result));
    return result;
  }

  /** Draws the text block annotations for position, size, and raw value on the supplied canvas. */
  @Override
  public void draw(Canvas canvas) {
    Log.d(TAG, "Text is: " + text.getText());
    // Save in scope label match list
    List<String> texts = new ArrayList<>();
    for (TextBlock textBlock : text.getTextBlocks()) {
      for (Line line : textBlock.getLines()) {
        String text = line.getText();
        if (isLabelInScope((text))) {
          Log.d(TAG, "Line text1 is: " + line.getText());
          texts.add(text.replace('O','0'));
        }
      }
    }
    for (TextBlock textBlock : text.getTextBlocks()) {
      // Renders the text at the bottom of the box.
      Log.d(TAG, "TextBlock text is: " + textBlock.getText());
      Log.d(TAG, "TextBlock boundingbox is: " + textBlock.getBoundingBox());
      Log.d(TAG, "TextBlock cornerpoint is: " + Arrays.toString(textBlock.getCornerPoints()));
      if (shouldGroupTextInBlocks) {
        String text =
            showLanguageTag
                ? String.format(
                    TEXT_WITH_LANGUAGE_TAG_FORMAT,
                    textBlock.getRecognizedLanguage(),
                    textBlock.getText())
                : textBlock.getText();
        drawText(
            text,
            new RectF(textBlock.getBoundingBox()),
            TEXT_SIZE * textBlock.getLines().size() + 2 * STROKE_WIDTH,
            canvas, false);
      } else {
        for (Line line : textBlock.getLines()) {
          Log.d(TAG, "Line text is: " + line.getText());
          Log.d(TAG, "Line boundingbox is: " + line.getBoundingBox());
          Log.d(TAG, "Line cornerpoint is: " + Arrays.toString(line.getCornerPoints()));
          Log.d(TAG, "Line confidence is: " + line.getConfidence());
          Log.d(TAG, "Line angle is: " + line.getAngle());
          String text =
              showLanguageTag
                  ? String.format(
                      TEXT_WITH_LANGUAGE_TAG_FORMAT, line.getRecognizedLanguage(), line.getText())
                  : line.getText();
          text =
              showConfidence
                  ? String.format(Locale.US, "%s (%.2f)", text, line.getConfidence())
                  : text;

          if (isLabelInScope((text))) {
              Log.d(TAG, "Line text count: " + Collections.frequency(texts, text));
              drawText(text, new RectF(line.getBoundingBox()), TEXT_SIZE + 2 * STROKE_WIDTH, canvas, isLabelMatch(texts, text));
          }

          for (Element element : line.getElements()) {
            Log.d(TAG, "Element text is: " + element.getText());
            Log.d(TAG, "Element boundingbox is: " + element.getBoundingBox());
            Log.d(TAG, "Element cornerpoint is: " + Arrays.toString(element.getCornerPoints()));
            Log.d(TAG, "Element language is: " + element.getRecognizedLanguage());
            Log.d(TAG, "Element confidence is: " + element.getConfidence());
            Log.d(TAG, "Element angle is: " + element.getAngle());
            for (Symbol symbol : element.getSymbols()) {
              Log.d(TAG, "Symbol text is: " + symbol.getText());
              Log.d(TAG, "Symbol boundingbox is: " + symbol.getBoundingBox());
              Log.d(TAG, "Symbol cornerpoint is: " + Arrays.toString(symbol.getCornerPoints()));
              Log.d(TAG, "Symbol confidence is: " + symbol.getConfidence());
              Log.d(TAG, "Symbol angle is: " + symbol.getAngle());
            }
          }
        }
      }
    }
  }

  private void drawText(String text, RectF rect, float textHeight, Canvas canvas, boolean isHighlight) {
    // If the image is flipped, the left will be translated to right, and the right to left.
    float x0 = translateX(rect.left);
    float x1 = translateX(rect.right);
    rect.left = min(x0, x1);
    rect.right = max(x0, x1);
    rect.top = translateY(rect.top);
    rect.bottom = translateY(rect.bottom);
    canvas.drawRect(rect, (isHighlight)? rectPaint2 : rectPaint);
    float textWidth = textPaint.measureText(text);
    canvas.drawRect(
        rect.left - STROKE_WIDTH,
        rect.top - textHeight,
        rect.left + textWidth + 2 * STROKE_WIDTH,
        rect.top,
        (isHighlight)? labelPaint2 : labelPaint);
    // Renders the text at the bottom of the box.
    canvas.drawText(text, rect.left, rect.top - STROKE_WIDTH, textPaint);
  }
}
