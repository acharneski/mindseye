/*
 * Copyright (c) 2018 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.lang;

import java.util.UUID;

/**
 * Interface for objects apply reference counting. Reference counted objects will be freed when the last reference is
 * freed, in a guaranteed-once-only manner. In general, valid reference counting behavior can be maintained by observing
 * a few rules: 1) References should be freed as soon as they are finished being used 2) Only reference counting objects
 * should be used to hold pointers to other reference counting objects, and those pointers should be freed and clears when
 * the object is freed. 3) If returning a reference to an object from a method, increment its reference count. 4) Handle
 * reference counted objects within the scope of a single method when possible. (i.e. prefer to keep them on the stack,
 * not heap.)
 */
public interface ReferenceCounting {

  /**
   * Current ref count int.
   *
   * @return the int
   */
  int currentRefCount();

  /**
   * Add ref.
   *
   * @return the reference counting base
   */
  ReferenceCountingBase addRef();

  /**
   * Free ref.
   */
  default void freeRef() {
  }

  /**
   * Free ref async.
   */
  default void freeRefAsync() {
  }

  /**
   * Claim ref.
   *
   * @param obj the obj
   */
  void claimRef(ReferenceCounting obj);

  /**
   * Add ref.
   *
   * @param obj the obj
   */
  void addRef(ReferenceCounting obj);

  /**
   * Is finalized boolean.
   *
   * @return the boolean
   */
  boolean isFinalized();

  /**
   * Assert alive.
   *
   * @return the boolean
   */
  boolean assertAlive();

  /**
   * Free ref.
   *
   * @param obj the obj
   */
  void freeRef(ReferenceCounting obj);

  /**
   * Gets object id.
   *
   * @return the object id
   */
  UUID getObjectId();

  /**
   * Sets floating.
   */
  ReferenceCountingBase detach();
}
