/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_CDS_CDSCONFIG_HPP
#define SHARE_CDS_CDSCONFIG_HPP

#include "memory/allStatic.hpp"
#include "utilities/macros.hpp"

class CDSConfig : public AllStatic {
public:
  // Basic CDS features
  static bool      is_dumping_archive()                      NOT_CDS_RETURN_(false);
  static bool      is_dumping_static_archive()               NOT_CDS_RETURN_(false);
  static bool      is_dumping_dynamic_archive()              NOT_CDS_RETURN_(false);

  // CDS archived heap
  static bool      is_dumping_heap()                         NOT_CDS_JAVA_HEAP_RETURN_(false);
};

#endif // SHARE_CDS_CDSCONFIG_HPP
