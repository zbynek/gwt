/*
 * Copyright 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dom.client;

/**
 * WebKit based implementation of {@link com.google.gwt.dom.client.DOMImplStandardBase}.
 */
class DOMImplWebkit extends DOMImplStandardBase {

  /**
   * WebKit events sometimes target the text node inside the element instead
   * of the element itself, so we need to get the parent of the text node.
   */
  @Override
  public native EventTarget eventGetTarget(NativeEvent evt) /*-{
    var target = evt.target;
    if (target && target.nodeType == 3) {
      target = target.parentNode;
    }
    return target;
  }-*/;

  @Override
  public native EventTarget eventGetCurrentTarget(NativeEvent event) /*-{
    return event.currentTarget || $wnd;
  }-*/;

  @Override
  public int getScrollLeft(Element elem) {
    if (!elem.hasTagName(BodyElement.TAG) && isRTL(elem)) {
      return super.getScrollLeft(elem)
          - (elem.getScrollWidth() - elem.getClientWidth());
    }
    return super.getScrollLeft(elem);
  }

  @Override
  public void setScrollLeft(Element elem, int left) {
    if (!elem.hasTagName(BodyElement.TAG) && isRTL(elem)) {
      left += elem.getScrollWidth() - elem.getClientWidth();
    }
    super.setScrollLeft(elem, left);
  }

  protected native boolean isRTL(Element elem) /*-{
    return elem.ownerDocument.defaultView.getComputedStyle(elem, '').direction == 'rtl';
  }-*/;
}

