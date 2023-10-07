/*
 * Copyright 2022 github.com/2m/rallyeye/contributors
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

package rallyeye

class UtilSuite extends munit.FunSuite:
  test("decodes HTML encoded glyphs"):
    val obtained = "&#25289&#26222&#20848&#24503 &#33832&#21346&#20304 GACHA RT".decodeHtmlUnicode
    val expected = "拉普兰德 萨卢佐 GACHA RT"
    assertEquals(obtained, expected)
