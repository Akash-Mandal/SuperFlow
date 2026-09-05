## 2025-05-18 - Contextual Content Descriptions for Reusable Info Buttons
**Learning:** Custom info buttons (`InfoButton`) initialized with static content descriptions like `"Info"` fail screen reader context when rendered in lists or parameter forms. Setting dynamic `contentDescription` based on the item's `title` (e.g. `"Info: Temperature"`) ensures TalkBack users know exactly which parameter or setting the info icon describes.
**Action:** Always update accessibility `contentDescription` on custom icon/info buttons whenever the associated title or property changes.
