/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

package leon.core;

import java.io.IOException;

public interface ToLeon {
  LeonPacker toLeon(LeonPacker packer) throws IOException;
}
