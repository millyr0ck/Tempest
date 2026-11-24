Aurora ClickGUI — category header icons
=======================================

Drop 32x32 PNG files in this folder to give each category column an icon on the
right-hand side of its header.

Filenames must match the category name in lowercase:

    combat.png
    movement.png
    player.png
    client.png
    visuals.png
    configs.png

Notes
-----
* Source images should be 32x32 with a transparent background. They are drawn at
  14x14 in the header, so 32x32 gives clean downscaling.
* White / light artwork reads best against the dark header.
* Any file that is missing is simply skipped — the header renders without an icon.
  There is no placeholder and no error, so you can add them one at a time.
* Files are looked up as  tempest/images/categories/<name>.png  and are cached after
  the first successful load.
