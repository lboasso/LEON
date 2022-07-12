/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

package leon.core;


public final class LeonException extends RuntimeException {
  public enum Reason {UnableToPackObj, UnableToUnpackObj, InvalidTag, InternalError}

  private final Reason reason;

  public LeonException(String s, Reason reason) {
    super(s);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
