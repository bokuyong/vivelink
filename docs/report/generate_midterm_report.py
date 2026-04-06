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

    summary_box = (MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 330)
    draw.rounded_rectangle(summary_box, radius=24, fill="#F6FAFB", outline=BORDER, width=2)
    body = (
        "본 프로젝트는 사용자의 음성 명령을 AI가 해석하여 원하는 Android 앱을 실행하고, "
        "필요한 조작까지 이어서 수행하는 자동화 시스템인 clodock을 구현하는 것을 목표로 한다. "
        "단순 반복 입력이나 고정 매크로에 머무르지 않고, 사용자의 자연어 표현을 이해하여 앱 선택, 화면 이동, 입력 수행을 "
        "연결하는 구조를 지향한다. 중간발표 시점에서는 전체 기능을 무리하게 확장하기보다, 실제 활용성을 가장 잘 보여줄 수 있는 "
        "대표 시나리오로 토스 송금 자동화를 우선 개발하고 있으며, 하드웨어 연동은 병행하여 준비 중이다."
    )
    draw.text((MARGIN_X + 28, y + 24), "보고서 개요", font=H2_FONT, fill=PRIMARY)
    draw_paragraph(draw, body, MARGIN_X + 28, y + 78, BODY_FONT, TEXT, CONTENT_WIDTH - 56, 12)

    y += 410
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
        "현재까지는 폰과 폰 환경을 기반으로 우선 테스트를 진행하며 핵심 동작 흐름을 검증하고 있다. "
        "다만 AI 자연어 해석 정확도와 화면 자동화의 안정성은 계속 보완 중이므로, 본 보고서에서는 완성 결과보다 "
        "개발 방향, 현재 구현 수준, 그리고 향후 확장 계획을 중심으로 정리하였다."
    )
    draw_paragraph(draw, note, MARGIN_X, y, BODY_FONT, MUTED, CONTENT_WIDTH, 12)
    draw_footer(draw, 1)
    return image


def build_page_two():
    image, draw = new_page()
    y = draw_header(draw, "1. 프로젝트 개요 및 추진 배경")

    y = draw_paragraph(
        draw,
        "clodock은 사용자의 음성 명령을 이해하고, 해당 의도에 맞는 Android 앱을 실행한 뒤 "
        "필요한 조작을 단계적으로 수행하는 시스템을 목표로 개발 중인 프로젝트이다. "
        "기존 자동화 방식이 고정된 좌표나 사전에 입력된 작업 목록에 의존하는 경우가 많았다면, "
        "본 프로젝트는 사용자의 문장을 해석해 상황에 맞는 동작 흐름을 구성하는 데 초점을 둔다.",
        MARGIN_X,
        y,
    ) + 20

    draw.text((MARGIN_X, y), "1.1 추진 배경", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "모바일 환경에서는 송금, 앱 실행, 메뉴 이동, 반복 입력과 같이 자주 수행되지만 단계가 많은 작업이 존재한다. "
        "특히 실제 사용자는 화면 구조를 세세하게 기억하기보다, 원하는 결과를 자연어로 표현하는 경우가 많다. "
        "이에 따라 음성 명령과 AI 해석을 결합한 자동화 방식은 단순 편의 기능을 넘어, 접근성과 사용성 측면에서도 의미 있는 접근이라고 판단하였다.",
        MARGIN_X,
        y,
    ) + 22

    draw.text((MARGIN_X, y), "1.2 개발 목표", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "사전에 정해진 명령만 수행하는 방식이 아니라, AI가 사용자의 자연어를 해석하여 의도에 맞는 동작을 구성하도록 한다.",
        "앱 실행, 화면 이동, 입력 자동화와 같이 실제 사용 빈도가 높은 작업을 우선 대상으로 삼는다.",
        "대표 시나리오를 먼저 안정화한 뒤 동일한 해석 구조를 다양한 앱으로 확장하는 것을 목표로 한다.",
    ], MARGIN_X, y) + 16

    draw.text((MARGIN_X, y), "1.3 현재 대표 시나리오 선정 이유", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "현재는 토스 앱 자동화를 대표 시나리오로 선정하였다. 토스 송금 과정은 "
        "수취인 해석, 금액 해석, 앱 실행, 화면 탐색, 단계별 입력, 최종 인증 분리와 같이 "
        "본 프로젝트의 핵심 요소를 한 번에 보여줄 수 있다는 점에서 적합하다. 또한 실사용과 맞닿아 있는 시나리오이기 때문에 "
        "AI 명령 처리의 정확도와 자동화 안정성을 함께 검증하기에 유의미한 사례라고 판단하였다.",
        MARGIN_X,
        y,
    ) + 20

    draw.text((MARGIN_X, y), "1.4 중간발표 기준 설명 방향", font=H2_FONT, fill=PRIMARY)
    y += 44
    draw_bullet_list(draw, [
        "clodock을 음성 기반 앱 자동화 시스템으로 새롭게 설계하고 구현 중이라는 점을 중심으로 설명",
        "하드웨어 연동 전체보다 현재 가장 구체적으로 보여줄 수 있는 토스 자동화 1차 구현 상태를 중심으로 설명",
        "이미 동작하는 부분과 아직 보완이 필요한 부분을 구분하여 전달함으로써 현재 개발 단계를 명확히 설명",
    ], MARGIN_X, y)

    draw_footer(draw, 2)
    return image


def build_page_three():
    image, draw = new_page()
    y = draw_header(draw, "2. 시스템 구성과 진행 현황")

    draw.text((MARGIN_X, y), "2.1 개발 접근 방식", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "본 프로젝트는 전체 기능을 한 번에 확장하기보다, 핵심 동작을 분해하여 단계별로 검증하는 방식으로 진행하고 있다. "
        "우선 사용자의 음성 명령을 텍스트로 수집하고, AI가 그 문장에서 앱 이름, 수취인, 금액, 수행 의도를 해석한 뒤, "
        "실제 앱 내부 화면을 순차적으로 조작하는 구조를 구성하고 있다. 민감한 인증 단계는 사용자에게 남겨 두어 "
        "자동화 편의성과 사용자 통제 사이의 균형을 확보하는 방향으로 설계하였다.",
        MARGIN_X,
        y,
    ) + 18

    y = draw_bullet_list(draw, [
        "음성 명령 입력과 자연어 해석을 결합한 구조로 설계",
        "명령 문장에서 핵심 요소를 추출한 뒤 앱 동작 시퀀스로 연결",
        "최종 지문 인증과 같은 민감 단계는 사용자가 직접 수행하도록 분리",
    ], MARGIN_X, y) + 18

    draw.text((MARGIN_X, y), "2.2 소프트웨어 진행 현황", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "앱의 기본 구조와 핵심 자동화 흐름은 우선 폰과 폰 환경에서 테스트하며 검증하였다.",
        "현재는 토스 송금 자동화를 대표 기능으로 삼아 자연어 해석과 단계별 화면 제어를 집중적으로 수정하고 있다.",
        "AI가 명령을 안정적으로 해석하지 못하는 경우가 있어, 명령 판단 규칙과 화면 대응 로직을 계속 보완 중이다.",
    ], MARGIN_X, y) + 18

    y = draw_paragraph(
        draw,
        "현재 단계에서 가장 중요한 목표는 모든 기능을 넓게 추가하는 것이 아니라, 대표 기능 하나를 실제로 신뢰할 수 있는 수준까지 끌어올리는 것이다. "
        "이를 통해 자연어 명령 해석, 앱 실행, 화면 전환 대응, 입력 정확도와 같은 핵심 요소를 먼저 정제하고, "
        "향후 다른 앱 자동화로 확장할 수 있는 기반을 마련하고자 한다.",
        MARGIN_X,
        y,
    ) + 22

    draw.text((MARGIN_X, y), "2.3 하드웨어 확보 상황", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "초기 계획상 필요한 하드웨어 구매가 지연되면서 개발 일정에 영향을 줄 가능성이 있었다. "
        "이를 보완하기 위해 Raspberry Pi 5, PA03 Android 기기, Radxa Cubie A7Z를 사비로 직접 구매하였다. "
        "프로젝트 일정상 하드웨어 도착만 기다리는 방식은 비효율적이라고 판단했기 때문에, "
        "우선 기기 확보와 소프트웨어 개발을 병행하는 방향으로 대응하였다. 현재는 각 기기에 Android를 올리고 초기 세팅을 진행하며, "
        "이후 기기별 구동 안정성과 앱 호환성까지 순차적으로 확인할 계획이다.",
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
        "즉, 하드웨어 전체 통합은 아직 진행 중이지만 프로젝트가 준비 단계에만 머물러 있는 것은 아니다. "
        "현재는 앱 구조 설계, 대표 기능 구현, 자연어 해석 보정, 자동화 흐름 검증이 동시에 진행되고 있으며, "
        "중간발표에서는 이러한 선행 개발 성과와 실제 구현 수준을 중심으로 설명하는 것이 적절하다.",
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
    y = draw_header(draw, "3. 토스 자동화 구현 현황")

    draw.text((MARGIN_X, y), "3.1 대표 시나리오 개요", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "현재 가장 집중하고 있는 기능은 토스 앱 송금 자동화이다. 예를 들어 사용자가 "
        "\"토스로 유세린한테 만원 송금해\"라고 말하면, 시스템이 해당 문장에서 송금 의도, 수취인, 금액을 해석한 뒤 "
        "토스 앱 내부의 송금 과정을 단계적으로 진행하는 흐름을 목표로 하고 있다.",
        MARGIN_X,
        y,
    ) + 22

    draw.text((MARGIN_X, y), "3.2 현재 구현 중인 핵심 기능", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "사용자가 자연어로 말한 문장에서 수취인 이름과 금액을 해석하도록 구현 중",
        "연락처 기반 송금을 기준으로 토스 앱 실행, 대상 선택, 금액 입력, 보내기 직전 단계까지 자동화 흐름을 구성 중",
        "최종 지문 인증 단계는 사용자가 직접 수행하도록 분리하여 실제 금융 동작에서의 안전성을 고려",
    ], MARGIN_X, y) + 22

    y = draw_paragraph(
        draw,
        "현재 구현 방향은 사용자가 자연어로 의도를 전달하면 시스템이 핵심 정보를 해석하고, "
        "토스 앱 내부의 여러 화면을 따라가며 필요한 조작을 순차적으로 수행한 뒤 마지막 민감 단계는 사용자에게 넘기는 구조이다. "
        "이는 자동화의 편의성을 확보하면서도 금융 거래 특성상 필요한 사용자 개입을 유지하기 위한 설계라고 볼 수 있다.",
        MARGIN_X,
        y,
    ) + 22

    draw.text((MARGIN_X, y), "3.3 기술적 의미", font=H2_FONT, fill=PRIMARY)
    y += 44
    draw_paragraph(
        draw,
        "토스 자동화는 단순한 앱 한 개 제어를 넘어, 자연어 해석 결과를 실제 앱 동작 시퀀스로 연결하는 핵심 구조를 검증한다는 점에서 의미가 있다. "
        "즉, 이 기능이 안정화되면 향후 다른 앱에도 동일한 방식의 해석과 자동화 로직을 확장할 수 있는 기반이 된다.",
        MARGIN_X,
        y,
    )

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


def build_page_five():
    image, draw = new_page()
    y = draw_header(draw, "4. 현재 한계와 개선 계획")

    draw.text((MARGIN_X, y), "4.1 현재 한계", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "AI 자연어 해석이 아직 완전하지 않아 표현 방식에 따라 오동작 가능성이 존재함",
        "폰과 폰 환경에서는 우선 테스트를 진행했지만, 하드웨어 3종과의 통합 검증은 아직 진행 중임",
        "현재는 토스 자동화를 중심으로 개발 중이므로, 범용 앱 자동화까지는 추가 설계와 검증이 더 필요함",
    ], MARGIN_X, y) + 22

    y = draw_paragraph(
        draw,
        "특히 자연어 명령 기반 자동화는 표현의 다양성과 실제 앱 화면의 변화에 영향을 받기 때문에, "
        "단순 구현 여부보다 안정성과 오인식 방지가 중요하다. 따라서 현재는 무리한 기능 확장보다 "
        "대표 기능의 정확도를 높이는 작업이 우선되어야 한다.",
        MARGIN_X,
        y,
    ) + 24

    draw.text((MARGIN_X, y), "4.2 개선 계획", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_bullet_list(draw, [
        "토스 송금 자동화를 먼저 안정화하여 명령 해석과 화면 제어의 신뢰도를 높일 계획",
        "수취인 판별, 금액 해석, 단계별 화면 인식 정확도를 계속 보정할 계획",
        "Raspberry Pi 5, PA03, Radxa Cubie A7Z의 Android 세팅 완료 후 기기별 테스트를 확대할 계획",
        "이후 앱 실행, 화면 이동, 반복 작업 자동화 등으로 기능 범위를 점진적으로 넓힐 계획",
    ], MARGIN_X, y) + 20

    draw_paragraph(
        draw,
        "결과적으로 현재 단계의 핵심 과제는 기능 수를 늘리는 것이 아니라, 음성 명령이 실제 동작으로 안정적으로 이어지는 경험을 만드는 것이다. "
        "토스 자동화에서 이 기반을 확보한 뒤 다른 앱으로 확장해야 전체 시스템의 완성도를 높일 수 있다.",
        MARGIN_X,
        y,
    )

    conclusion_box = (MARGIN_X, 1180, PAGE_WIDTH - MARGIN_X, 1485)
    draw.rounded_rectangle(conclusion_box, radius=22, fill="#F7FBFC", outline=BORDER, width=2)
    draw.text((conclusion_box[0] + 24, conclusion_box[1] + 20), "중간 단계 평가", font=H2_FONT, fill=PRIMARY)
    draw_paragraph(
        draw,
        "현재 clodock은 아직 모든 기능이 완성된 상태는 아니지만, 핵심 개념을 실제 동작으로 검증하는 단계에 들어와 있다. "
        "즉, 아이디어 제안 수준을 넘어 자연어 해석과 앱 자동화를 실제로 연결하는 구현 단계가 진행되고 있으며, "
        "대표 기능인 토스 자동화를 통해 시스템의 방향성과 가능성을 구체적으로 보여주고 있다.",
        conclusion_box[0] + 24,
        conclusion_box[1] + 78,
        BODY_FONT,
        TEXT,
        (conclusion_box[2] - conclusion_box[0]) - 48,
        12,
    )

    draw_footer(draw, 5)
    return image


def build_page_six():
    image, draw = new_page()
    y = draw_header(draw, "5. 기대 효과 및 결론")

    draw.text((MARGIN_X, y), "5.1 기대 효과", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "본 프로젝트가 안정화되면 사용자는 복잡한 앱 조작 절차를 일일이 기억하지 않아도, "
        "자연어 명령만으로 원하는 작업을 보다 직관적으로 수행할 수 있게 된다. 이는 단순한 편의성 향상뿐 아니라 "
        "반복 작업에 소요되는 시간과 인지 부담을 줄이는 측면에서도 의미가 있다.",
        MARGIN_X,
        y,
    ) + 18

    y = draw_bullet_list(draw, [
        "음성 명령을 통한 직관적인 앱 제어 가능",
        "반복 작업 자동화로 사용자 부담과 소요 시간 감소",
        "AI 해석 기반 구조를 통해 다양한 앱과 작업으로의 확장 가능",
    ], MARGIN_X, y) + 22

    draw.text((MARGIN_X, y), "5.2 결론", font=H2_FONT, fill=PRIMARY)
    y += 44
    y = draw_paragraph(
        draw,
        "clodock은 사용자의 명령을 이해하고 실제 앱 동작으로 이어지게 하는 AI 기반 Android 자동화 시스템으로 개발 중이다. "
        "중간발표 시점에서는 하드웨어 통합이 모두 끝난 상태는 아니지만, 대표 기능인 토스 자동화를 중심으로 핵심 구조를 실제 구현하고 검증하고 있다.",
        MARGIN_X,
        y,
    ) + 20

    y = draw_paragraph(
        draw,
        "특히 본 프로젝트는 단순 매크로 반복이 아니라 자연어 해석, 화면 단계 판단, 사용자 인증 분리까지 포함한 구조를 목표로 한다는 점에서 차별성이 있다. "
        "앞으로는 토스 자동화의 안정도를 높인 뒤, 하드웨어 3종과의 연동 검증을 거쳐 더 다양한 앱과 기능으로 범위를 넓혀갈 예정이다.",
        MARGIN_X,
        y,
    )

    summary_box = (MARGIN_X, 1230, PAGE_WIDTH - MARGIN_X, 1490)
    draw.rounded_rectangle(summary_box, radius=22, fill="#F7FBFC", outline=BORDER, width=2)
    draw.text((summary_box[0] + 24, summary_box[1] + 20), "한 줄 정리", font=H2_FONT, fill=PRIMARY)
    draw_paragraph(
        draw,
        "clodock은 음성 명령을 실제 앱 동작으로 연결하는 Android 자동화 시스템이며, "
        "현재는 토스 송금 자동화를 시작점으로 기능 안정화와 다중 하드웨어 확장을 함께 추진하고 있다.",
        summary_box[0] + 24,
        summary_box[1] + 78,
        BODY_FONT,
        TEXT,
        (summary_box[2] - summary_box[0]) - 48,
        12,
    )

    draw_footer(draw, 6)
    return image


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pages = [
        build_cover(),
        build_page_two(),
        build_page_three(),
        build_page_four(),
        build_page_five(),
        build_page_six(),
    ]
    pages[0].save(OUT_PREVIEW)
    pages[0].save(OUT_PDF, save_all=True, append_images=pages[1:], resolution=150.0)
    print(OUT_PDF)


if __name__ == "__main__":
    main()
