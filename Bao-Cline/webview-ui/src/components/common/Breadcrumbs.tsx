import React from 'react';

interface BreadcrumbSegment {
  label: string;
  onClick?: () => void;
}

interface BreadcrumbsProps {
  segments: BreadcrumbSegment[];
}

const Breadcrumbs: React.FC<BreadcrumbsProps> = ({ segments }) => {
  if (!segments || segments.length === 0) {
    return null;
  }

  return (
    <nav aria-label="breadcrumb" className="text-sm text-vscode-breadcrumb-foreground">
      {segments.map((segment, index) => (
        <React.Fragment key={index}>
          {index > 0 && <span className="mx-1 text-vscode-breadcrumb-separatorForeground">{'>'}</span>}
          {segment.onClick && index < segments.length - 1 ? (
            <button
              onClick={segment.onClick}
              className="hover:underline focus:outline-none focus:underline text-vscode-breadcrumb-activeForeground"
              aria-current={index === segments.length - 1 ? 'page' : undefined}
            >
              {segment.label}
            </button>
          ) : (
            <span
              className={index === segments.length - 1 ? "text-vscode-breadcrumb-focusForeground font-medium" : ""}
              aria-current={index === segments.length - 1 ? 'page' : undefined}
            >
              {segment.label}
            </span>
          )}
        </React.Fragment>
      ))}
    </nav>
  );
};

export default Breadcrumbs;
