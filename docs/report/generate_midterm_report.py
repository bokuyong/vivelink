from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


BASE_DIR = Path("/Users/bogguyong/Downloads/vivelink-main 2")
OUT_DIR = BASE_DIR / "docs" / "report"
OUT_PDF = OUT_DIR / "clodock_midterm_report_2026-04-06.pdf"
OUT_PREVIEW = OUT_DIR / "clodock_midterm_report_2026-04-06_preview.png"

PAGE_WIDTH = 1240
PAGE_HEIGHT = 1754
MARGIN_X = 96
MARGIN_Y = 96
CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

BG = "#FFFFFF"
TEXT = "#111111"
MUTED = "#555555"
PRIMARY = "#184F56"
LIGHT = "#E8EFF1"
BORDER = "#C8D7DC"

FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
TITLE_FONT = ImageFont.truetype(FONT_PATH, 52)
H1_FONT = ImageFont.truetype(FONT_PATH, 34)
H2_FONT = ImageFont.truetype(FONT_PATH, 28)
BODY_FONT = ImageFont.truetype(FONT_PATH, 24)
SMALL_FONT = ImageFont.truetype(FONT_PATH, 20)

SCREENSHOT_PATHS = [
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 005.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 003.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 001.jpeg"),
]


def new_page():
    image = Image.new("RGB", (PAGE_WIDTH, PAGE_HEIGHT), BG)
    draw = ImageDraw.Draw(image)
    return image, draw


def text_width(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0]


def line_height(draw, font):
    box = draw.textbbox((0, 0), "가나다ABC123", font=font)
    return box[3] - box[1]


def wrap_text(draw, text, font, max_width):
    lines = []
    current = ""
    for ch in text:
        candidate = current + ch
        if ch == "\n":
            lines.append(current.rstrip())
            current = ""
            continue
        if text_width(draw, candidate, font) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current.rstrip())
            current = ch.lstrip()
    if current:
        lines.append(current.rstrip())
    return lines


def draw_paragraph(draw, text, x, y, font=BODY_FONT, fill=TEXT, max_width=CONTENT_WIDTH, gap=12):
    lines = wrap_text(draw, text, font, max_width)
    lh = line_height(draw, font)
    current_y = y
    for line in lines:
        draw.text((x, current_y), line, font=font, fill=fill)
        current_y += lh + gap
    return current_y


def draw_bullet_list(draw, items, x, y, max_width=CONTENT_WIDTH):
    current_y = y
    for item in items:
        draw.text((x, current_y), "•", font=BODY_FONT, fill=PRIMARY)
        current_y = draw_paragraph(draw, item, x + 28, current_y, BODY_FONT, TEXT, max_width - 28, 10) + 6
    return current_y


def draw_header(draw, page_title):
    draw.text((MARGIN_X, MARGIN_Y), page_title, font=H1_FONT, fill=PRIMARY)
    draw.line((MARGIN_X, MARGIN_Y + 52, PAGE_WIDTH - MARGIN_X, MARGIN_Y + 52), fill=LIGHT, width=3)
    return MARGIN_Y + 84


def draw_footer(draw, page_no):
    footer = f"clodock 중간보고서 | {page_no}"
    draw.line((MARGIN_X, PAGE_HEIGHT - 70, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 70), fill=LIGHT, width=2)
    draw.text((MARGIN_X, PAGE_HEIGHT - 52), footer, font=SMALL_FONT, fill=MUTED)


def build_cover():
    image, draw = new_page()
    y = 220
    draw.text((MARGIN_X, y), "clodock 중간보고서", font=TITLE_FONT, fill=TEXT)
    y += 92
    draw.text((MARGIN_X, y), "음성 기반 Android 앱 제어 및 자동화 시스템", font=H1_FONT, fill=PRIMARY)
    y += 110

    summary_box = (MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 300)
    draw.rounded_rectangle(summary_box, radius=24, fill="#F6FAFB", outline=BORDER, width=2)
    body = (
        "본 프로젝트는 사용자의 음성 명령을 AI가 해석하여 원하는 Android 앱을 실행하고, "
        "필요한 동작까지 이어서 수행하는 자동화 시스템을 목표로 한다. "
        "현재 중간 단계에서는 대표 시나리오로 토스 송금 자동화를 우선 개발하고 있으며, "
        "하드웨어 통합은 병행 진행 중이다."
    )
    draw.text((MARGIN_X + 28, y + 24), "보고서 개요", font=H2_FONT, fill=PRIMARY)
    draw_paragraph(draw, body, MARGIN_X + 28, y + 78, BODY_FONT, TEXT, CONTENT_WIDTH - 56, 12)

    y += 380
    draw.text((MARGIN_X, y), "작성일", font=H2_FONT, fill=PRIMARY)
    draw.text((MARGIN_X + 170, y), "2026.04.06", font=BODY_FONT, fill=TEXT)
    y += 54
    draw.text((MARGIN_X, y), "프로젝트명", font=H2_FONT, fill=PRIMARY)
    draw.text((MARGIN_X + 170, y), "clodock", font=BODY_FONT, fill=TEXT)
    y += 54
    draw.text((MARGIN_X, y), "현재 중점 기능", font=H2_FONT, fill=PRIMARY)
    draw.text((MARGIN_X + 170, y), "토스 송금 자동화", font=BODY_FONT, fill=TEXT)

    y += 130
    note = (
        "중간발표 시점 기준으로 앱은 폰과 폰 환경에서 먼저 테스트를 진행했으며, "
        "AI 자연어 해석 정확도와 자동화 안정성은 계속 수정 중이다."
    )
    draw_paragraph(draw, note, MARGIN_X, y, BODY_FONT, MUTED, CONTENT_WIDTH, 12)
    draw_footer(draw, 1)
    return image


def build_page_two():
    image, draw = new_page()
    y = draw_header(draw, "1. 프로젝트 개요 및 개발 배경")

    y = draw_paragraph(
        draw,
        "clodock은 사용자의 음성 명령을 이해하고, 해당 명령에 맞는 Android 앱을 실행한 뒤 "
        "필요한 동작을 자동으로 수행하는 시스템을 목표로 개발 중인 프로젝트이다.",
        MARGIN_X,
        y,
    ) + 20

    draw.text((MARGIN_X, y), "1.1 개발 목표", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "고정된 명령만 수행하는 방식이 아니라, AI가 사용자의 자연어를 해석하는 구조를 구현한다.",
        "원하는 앱 실행, 화면 이동, 반복 작업 수행 등으로 기능 범위를 점진적으로 확장한다.",
        "대표 시나리오를 먼저 안정화한 뒤, 다양한 앱 자동화로 확대한다.",
    ], MARGIN_X, y) + 16

    draw.text((MARGIN_X, y), "1.2 현재 대표 시나리오", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "현재는 토스 앱 자동화를 대표 시나리오로 선택하였다. "
        "그 이유는 음성 명령 해석, 특정 앱 실행, 화면 탐색, 단계별 자동화 흐름을 "
        "하나의 기능 안에서 비교적 명확하게 보여줄 수 있기 때문이다.",
        MARGIN_X,
        y,
    ) + 20

    draw.text((MARGIN_X, y), "1.3 중간발표 기준 설명 방향", font=H2_FONT, fill=PRIMARY)
    y += 44
    draw_bullet_list(draw, [
        "새롭게 설계한 clodock 앱이라는 점을 중심으로 설명",
        "하드웨어 연동 전체보다 토스 자동화 1차 구현 상태를 중심으로 설명",
        "현재 구현된 부분과 아직 보완 중인 부분을 분리해서 설명",
    ], MARGIN_X, y)

    draw_footer(draw, 2)
    return image


def build_page_three():
    image, draw = new_page()
    y = draw_header(draw, "2. 진행 현황 및 하드웨어 확보 상황")

    draw.text((MARGIN_X, y), "2.1 소프트웨어 진행 현황", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "기존 vivelink 프로젝트를 기반으로 하되, clodock이라는 새로운 구조로 재설계 중이다.",
        "앱은 폰과 폰 환경에서 먼저 테스트를 진행했다.",
        "현재는 AI가 자연어를 안정적으로 해석하도록 수정 중이며, 대표 기능으로 토스 자동화를 우선 개발하고 있다.",
    ], MARGIN_X, y) + 18

    draw.text((MARGIN_X, y), "2.2 하드웨어 확보 상황", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "초기 계획상 필요한 하드웨어 구매가 지연되면서 개발 일정에 영향을 줄 가능성이 있었다. "
        "이를 보완하기 위해 Raspberry Pi 5, PA03 Android 기기, Radxa Cubie A7Z를 사비로 직접 구매하였다.",
        MARGIN_X,
        y,
    ) + 18

    draw_bullet_list(draw, [
        "Raspberry Pi 5 확보 완료",
        "PA03 Android 기기 확보 완료",
        "Radxa Cubie A7Z 확보 완료",
        "현재는 각 기기에 Android 올리기 및 초기 세팅 진행 중",
    ], MARGIN_X, y)

    box = (MARGIN_X, 1120, PAGE_WIDTH - MARGIN_X, 1490)
    draw.rounded_rectangle(box, radius=22, fill="#F8FBFC", outline=BORDER, width=2)
    draw.text((box[0] + 24, box[1] + 20), "정리", font=H2_FONT, fill=PRIMARY)
    draw_paragraph(
        draw,
        "즉, 하드웨어 전체 통합은 아직 진행 중이지만, 앱 구조 설계와 대표 기능 개발은 선행해서 진행하고 있다. "
        "중간발표에서는 이 선행 개발 상태를 중심으로 설명하는 것이 적절하다.",
        box[0] + 24,
        box[1] + 78,
        BODY_FONT,
        TEXT,
        (box[2] - box[0]) - 48,
        12,
    )

    draw_footer(draw, 3)
    return image


def paste_figure(base_image, image_path, box):
    if not image_path.exists():
        return
    figure = Image.open(image_path).convert("RGB")
    figure.thumbnail((box[2] - box[0], box[3] - box[1]))
    x = box[0] + ((box[2] - box[0]) - figure.width) // 2
    y = box[1] + ((box[3] - box[1]) - figure.height) // 2
    base_image.paste(figure, (x, y))


def build_page_four():
    image, draw = new_page()
    y = draw_header(draw, "3. 현재 구현 기능, 한계, 향후 계획")

    draw.text((MARGIN_X, y), "3.1 현재 구현 중인 핵심 기능", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "사용자가 예를 들어 \"토스로 유세린한테 만원 송금해\"와 같이 말하면, AI가 수취인과 금액을 해석하도록 구현 중",
        "토스 앱을 실행하고 연락처 검색, 금액 입력, 보내기 단계까지 자동화하는 흐름을 우선 개발 중",
        "최종 인증 단계는 사용자 지문 인증으로 남겨 안전성을 유지하는 방향으로 설계",
    ], MARGIN_X, y) + 22

    draw.text((MARGIN_X, y), "3.2 현재 한계", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "AI 자연어 해석이 아직 완전하지 않아 오동작 가능성이 있음",
        "폰-폰 환경에서는 테스트했지만, 하드웨어 3종과의 통합 검증은 아직 진행 중",
        "현재는 토스 자동화부터 시작하는 단계이며, 범용 앱 자동화까지는 확장 과정이 더 필요함",
    ], MARGIN_X, y) + 26

    draw.text((MARGIN_X, y), "3.3 향후 계획", font=H2_FONT, fill=PRIMARY)
    y += 44
    draw_bullet_list(draw, [
        "토스 송금 자동화를 먼저 안정화",
        "AI 명령 해석 정확도 향상",
        "하드웨어 3종 Android 세팅 완료 후 기기별 테스트 확대",
        "이후 앱 실행, 화면 이동, 반복 작업 자동화 등으로 범위 확장",
    ], MARGIN_X, y)

    figure_box_y = 1140
    draw.text((MARGIN_X, figure_box_y - 46), "참고 화면", font=H2_FONT, fill=PRIMARY)
    image_boxes = [
        (MARGIN_X, figure_box_y, 390, 1600),
        (430, figure_box_y, 724, 1600),
        (764, figure_box_y, 1058, 1600),
    ]
    captions = ["최종 보내기 화면", "금액 입력 화면", "토스 홈 화면"]
    for box, path, caption in zip(image_boxes, SCREENSHOT_PATHS, captions):
        draw.rounded_rectangle(box, radius=18, fill="#FAFCFD", outline=BORDER, width=2)
        paste_figure(image, path, (box[0] + 10, box[1] + 10, box[2] - 10, box[3] - 50))
        draw.text((box[0] + 18, box[3] - 38), caption, font=SMALL_FONT, fill=MUTED)

    draw_footer(draw, 4)
    return image


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pages = [
        build_cover(),
        build_page_two(),
        build_page_three(),
        build_page_four(),
    ]
    pages[0].save(OUT_PREVIEW)
    pages[0].save(OUT_PDF, save_all=True, append_images=pages[1:], resolution=150.0)
    print(OUT_PDF)


if __name__ == "__main__":
    main()
