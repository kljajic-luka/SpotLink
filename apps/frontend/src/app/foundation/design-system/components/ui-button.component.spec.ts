import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UiButtonComponent } from './ui-button.component';

describe('UiButtonComponent', () => {
  let fixture: ComponentFixture<UiButtonComponent>;
  let component: UiButtonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UiButtonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(UiButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('emituje clicked kada je dugme aktivno', () => {
    const clickedSpy = jasmine.createSpy('clicked');
    component.clicked.subscribe(clickedSpy);

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();

    expect(clickedSpy).toHaveBeenCalled();
  });

  it('ne emituje clicked kada je loading', () => {
    const clickedSpy = jasmine.createSpy('clicked');
    component.clicked.subscribe(clickedSpy);

    component.loading = true;
    const event = {
      preventDefault: jasmine.createSpy('preventDefault'),
      stopPropagation: jasmine.createSpy('stopPropagation'),
    } as unknown as MouseEvent;

    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(event.stopPropagation).toHaveBeenCalled();
    expect(clickedSpy).not.toHaveBeenCalled();
  });
});
