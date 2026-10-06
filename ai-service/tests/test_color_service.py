from PIL import Image

from app.services.color_service import extract_dominant_colors


def test_extracts_dominant_color_from_uniform_image() -> None:
    colors = extract_dominant_colors(Image.new("RGB", (32, 32), "white"))

    assert colors[0].name == "white"
    assert colors[0].ratio == 1.0


def test_color_ratios_are_sorted_and_sum_to_one() -> None:
    image = Image.new("RGB", (100, 20), "white")
    for x in range(25):
        for y in range(20):
            image.putpixel((x, y), (200, 35, 45))

    colors = extract_dominant_colors(image)

    assert colors[0].ratio >= colors[-1].ratio
    assert abs(sum(color.ratio for color in colors) - 1.0) < 1e-5
    assert {color.name for color in colors} >= {"white", "red"}
