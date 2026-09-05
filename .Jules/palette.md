## 2025-02-18 - Search Command Palette Empty States
**Learning:** When search/command overlays receive query input with no matching results, omitting an explicit empty state leaves users uncertain if the query is still processing or returned zero results.
**Action:** Always provide an explicit empty state with actionable suggestions when filter/search queries yield empty result lists.
## 2025-05-18 - Contextual Content Descriptions for Reusable Info Buttons
**Learning:** Custom info buttons (`InfoButton`) initialized with static content descriptions like `"Info"` fail screen reader context when rendered in lists or parameter forms. Setting dynamic `contentDescription` based on the item's `title` (e.g. `"Info: Temperature"`) ensures TalkBack users know exactly which parameter or setting the info icon describes.
**Action:** Always update accessibility `contentDescription` on custom icon/info buttons whenever the associated title or property changes.
