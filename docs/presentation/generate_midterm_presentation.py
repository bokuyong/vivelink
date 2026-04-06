from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


BASE_DIR = Path("/Users/bogguyong/Downloads/vivelink-main 2")
OUT_DIR = BASE_DIR / "docs" / "presentation"
OUT_PDF = OUT_DIR / "clodock_midterm_presentation_2026-04-06.pdf"
OUT_PREVIEW = OUT_DIR / "clodock_midterm_presentation_2026-04-06_preview.png"

WIDTH = 1600
HEIGHT = 900
MARGIN_X = 110
MARGIN_Y = 78

BG = "#F4F7F8"
SURFACE = "#FFFFFF"
PRIMARY = "#1F6F78"
PRIMARY_DARK = "#184F56"
SECONDARY = "#2E8B57"
TEXT = "#102A32"
MUTED = "#55727A"
LIGHT_BORDER = "#D5E1E5"
ALERT = "#C45B3C"

FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"

TITLE_FONT = ImageFont.truetype(FONT_PATH, 62)
SUBTITLE_FONT = ImageFont.truetype(FONT_PATH, 32)
SECTION_FONT = ImageFont.truetype(FONT_PATH, 38)
BODY_FONT = ImageFont.truetype(FONT_PATH, 34)
SMALL_FONT = ImageFont.truetype(FONT_PATH, 24)
CAPTION_FONT = ImageFont.truetype(FONT_PATH, 24)

SCREENSHOT_PATHS = [
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 005.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 004.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 003.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 002.jpeg"),
    Path("/Users/bogguyong/Downloads/KakaoTalk_Photo_2026-04-06-12-00-23 001.jpeg"),
]


def text_width(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0]


def wrap_text(draw, text, font, max_width):
    words = text.split(" ")
    lines = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if text_width(draw, candidate, font) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
                current = word
            else:
                lines.append(word)
                current = ""
    if current:
        lines.append(current)
    return lines


def draw_multiline(draw, text, x, y, font, fill, max_width, line_gap=10):
    lines = wrap_text(draw, text, font, max_width)
    current_y = y
    for line in lines:
        draw.text((x, current_y), line, font=font, fill=fill)
        box = draw.textbbox((x, current_y), line, font=font)
        current_y += (box[3] - box[1]) + line_gap
    return current_y


def draw_card(draw, box, title, bullets, accent=PRIMARY):
    x1, y1, x2, y2 = box
    draw.rounded_rectangle(box, radius=28, fill=SURFACE, outline=LIGHT_BORDER, width=2)
    draw.rounded_rectangle((x1 + 20, y1 + 20, x1 + 150, y1 + 60), radius=16, fill=accent)
    draw.text((x1 + 36, y1 + 28), title, font=CAPTION_FONT, fill="white")

    current_y = y1 + 88
    max_width = (x2 - x1) - 60
    for bullet in bullets:
        draw.ellipse((x1 + 28, current_y + 11, x1 + 40, current_y + 23), fill=accent)
        current_y = draw_multiline(draw, bullet, x1 + 56, current_y, BODY_FONT, TEXT, max_width - 28, 8) + 10


def draw_title(draw, title, subtitle):
    draw.text((MARGIN_X, MARGIN_Y), title, font=TITLE_FONT, fill=TEXT)
    draw.text((MARGIN_X, MARGIN_Y + 86), subtitle, font=SUBTITLE_FONT, fill=MUTED)
    draw.rounded_rectangle((MARGIN_X, MARGIN_Y + 150, MARGIN_X + 220, MARGIN_Y + 164), radius=8, fill=PRIMARY)


def slide_canvas():
    image = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(image)
    return image, draw


def make_cover():
    image, draw = slide_canvas()
    draw.rounded_rectangle((80, 80, WIDTH - 80, HEIGHT - 80), radius=40, fill=SURFACE)
    draw.rounded_rectangle((MARGIN_X, 120, MARGIN_X + 260, 168), radius=22, fill=PRIMARY)
    draw.text((MARGIN_X + 26, 130), "캡스톤 디자인 중간발표", font=CAPTION_FONT, fill="white")

    draw.text((MARGIN_X, 230), "clodock", font=ImageFont.truetype(FONT_PATH, 92), fill=TEXT)
    draw.text((MARGIN_X, 344), "음성 기반 Android 앱 제어 및 자동화 시스템", font=SECTION_FONT, fill=PRIMARY_DARK)
    draw.text((MARGIN_X, 410), "현재는 토스 송금 자동화를 1차 목표로 진행 중이며,\nAI 상태에 따라 점차 적용 범위를 확대할 계획입니다.", font=BODY_FONT, fill=MUTED, spacing=14)

    draw_card(
        draw,
        (MARGIN_X, 560, 720, 790),
        "발표 핵심",
        [
            "새롭게 설계한 clodock 앱 중심 개발",
            "하드웨어 지연 상황에서도 앱 개발 선행",
            "중간발표는 토스 자동화 중심 설명",
        ],
        PRIMARY,
    )
    draw_card(
        draw,
        (770, 560, WIDTH - 110, 790),
        "현재 메시지",
        [
            "폰-폰 환경에서 선행 테스트 완료",
            "AI 판단 정확도는 계속 수정 중",
            "하드웨어 연동은 병행 진행 중",
        ],
        SECONDARY,
    )

    return image


def make_overview():
    image, draw = slide_canvas()
    draw_title(draw, "01. 프로젝트 개요", "무엇을 만드는지 교수님께 짧고 명확하게 설명하는 장")

    draw_card(
        draw,
        (MARGIN_X, 280, 720, 760),
        "프로젝트 목표",
        [
            "음성 명령을 이해하는 Android 자동화 앱 개발",
            "AI 기반 자연어 해석 구조 지향",
            "앱 실행, 화면 이동, 작업 자동화까지 확장 예정",
        ],
        PRIMARY,
    )
    draw_card(
        draw,
        (770, 280, WIDTH - 110, 760),
        "현재 발표 기준",
        [
            "대표 시나리오는 토스 송금 자동화",
            "AI 해석과 앱 조작 능력을 함께 보여주기 좋음",
            "중간발표는 1차 구현과 확장 계획 위주 설명",
        ],
        SECONDARY,
    )
    return image


def make_progress():
    image, draw = slide_canvas()
    draw_title(draw, "02. 진행 현황", "개발 진행 상태와 하드웨어 상황을 간단히 정리하는 장")

    draw_card(
        draw,
        (MARGIN_X, 260, 700, 760),
        "앱 개발 진행",
        [
            "기존 프로젝트를 clodock 구조로 재설계 중",
            "음성 입력, 명령 해석, 토스 자동화 우선 구현",
            "폰-폰 테스트 완료, AI 정확도는 계속 수정 중",
        ],
        PRIMARY,
    )
    draw_card(
        draw,
        (740, 260, WIDTH - 110, 760),
        "하드웨어 진행",
        [
            "예정된 하드웨어 구매가 지연됨",
            "사비로 Raspberry Pi 5, PA03, Radxa Cubie A7Z 구매",
            "현재 Android 올리기와 초기 세팅 진행 중",
        ],
        ALERT,
    )

    draw.rounded_rectangle((MARGIN_X, 160, WIDTH - 110, 220), radius=22, fill="#E7F0F2")
    draw.text((MARGIN_X + 24, 176), "핵심 메시지: 하드웨어 통합은 진행 중이지만, 대표 자동화 시나리오는 이미 선행 개발 중", font=BODY_FONT, fill=PRIMARY_DARK)
    return image


def paste_screenshot(base_image, image_path, box):
    if not image_path.exists():
        return
    shot = Image.open(image_path).convert("RGB")
    shot.thumbnail((box[2] - box[0], box[3] - box[1]))
    paste_x = box[0] + ((box[2] - box[0]) - shot.width) // 2
    paste_y = box[1] + ((box[3] - box[1]) - shot.height) // 2
    base_image.paste(shot, (paste_x, paste_y))


def make_toss_flow():
    image, draw = slide_canvas()
    draw_title(draw, "03. 현재 구현 중인 대표 기능", "토스 송금 자동화 흐름을 중심으로 설명하는 장")

    flow_titles = ["토스 홈", "수취인 선택", "금액 입력", "최종 보내기"]
    flow_boxes = [
        (110, 280, 430, 700),
        (450, 280, 770, 700),
        (790, 280, 1110, 700),
        (1130, 280, 1450, 700),
    ]
    flow_images = [SCREENSHOT_PATHS[4], SCREENSHOT_PATHS[3], SCREENSHOT_PATHS[2], SCREENSHOT_PATHS[0]]

    for title, box, img_path in zip(flow_titles, flow_boxes, flow_images):
        draw.rounded_rectangle(box, radius=28, fill=SURFACE, outline=LIGHT_BORDER, width=2)
        draw.text((box[0] + 20, box[1] - 52), title, font=SECTION_FONT, fill=TEXT)
        paste_screenshot(image, img_path, (box[0] + 16, box[1] + 18, box[2] - 16, box[3] - 16))

    draw.rounded_rectangle((110, 740, WIDTH - 110, 820), radius=24, fill="#EEF6F7")
    draw.text(
        (134, 760),
        "예시: \"토스로 유세린한테 만원 송금해\" → 수취인/금액 해석 → 토스 자동 실행 → 연락처 선택 → 금액 입력 → 보내기 → 사용자 지문 인증",
        font=BODY_FONT,
        fill=PRIMARY_DARK,
    )
    return image


def make_issue_response():
    image, draw = slide_canvas()
    draw_title(draw, "04. 현재 이슈와 대응", "교수님 질문에 대비해 문제와 해결 방향을 한 장으로 정리")

    draw_card(
        draw,
        (MARGIN_X, 270, 720, 760),
        "현재 이슈",
        [
            "AI 자연어 해석이 아직 완벽하지 않음",
            "하드웨어별 Android 환경 차이 존재",
            "금융 앱 자동화는 정확성과 안정성이 중요함",
        ],
        ALERT,
    )
    draw_card(
        draw,
        (770, 270, WIDTH - 110, 760),
        "대응 방향",
        [
            "1차 범위를 토스 연락처 송금으로 제한",
            "마지막 인증은 사용자 지문으로 남겨 안전성 확보",
            "기기 준비 후 UI/성능 차이를 반영해 보정 진행",
        ],
        SECONDARY,
    )
    return image


def make_plan():
    image, draw = slide_canvas()
    draw_title(draw, "05. 다음 단계", "중간발표 이후의 계획을 짧게 마무리하는 장")

    draw_card(
        draw,
        (MARGIN_X, 280, WIDTH - 110, 760),
        "향후 계획",
        [
            "토스 송금 자동화부터 안정화",
            "하드웨어 3종 Android 세팅 완료 후 기기별 테스트 확대",
            "이후 앱 실행, 화면 이동, 반복 작업 자동화로 확장",
            "최종 목표는 AI가 말한 명령을 이해하고 필요한 동작까지 수행하는 시스템 구현",
        ],
        PRIMARY,
    )

    draw.rounded_rectangle((MARGIN_X, 170, WIDTH - 110, 230), radius=22, fill=PRIMARY)
    draw.text((MARGIN_X + 24, 184), "중간발표 한 줄 요약: 현재는 토스 자동화를 시작점으로 AI 기반 앱 제어를 구현하고 있습니다.", font=BODY_FONT, fill="white")
    return image


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    slides = [
        make_cover(),
        make_overview(),
        make_progress(),
        make_toss_flow(),
        make_issue_response(),
        make_plan(),
    ]
    slides[0].save(OUT_PREVIEW)
    slides[0].save(OUT_PDF, save_all=True, append_images=slides[1:], resolution=150.0)
    print(OUT_PDF)


if __name__ == "__main__":
    main()
